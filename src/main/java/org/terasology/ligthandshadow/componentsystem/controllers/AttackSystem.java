/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.ligthandshadow.componentsystem.controllers;

import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.ligthandshadow.componentsystem.LASUtils;
import org.terasology.ligthandshadow.componentsystem.components.BlackFlagComponent;
import org.terasology.ligthandshadow.componentsystem.components.FlagDropOnActivateComponent;
import org.terasology.ligthandshadow.componentsystem.components.HasFlagComponent;
import org.terasology.ligthandshadow.componentsystem.components.LASTeamComponent;
import org.terasology.ligthandshadow.componentsystem.components.RaycastOnActivateComponent;
import org.terasology.ligthandshadow.componentsystem.components.RedFlagComponent;
import org.terasology.logic.characters.CharacterHeldItemComponent;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.logic.inventory.InventoryManager;
import org.terasology.logic.inventory.events.DropItemRequest;
import org.terasology.logic.inventory.events.InventorySlotChangedEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.players.PlayerCharacterComponent;
import org.terasology.math.geom.Vector3f;
import org.terasology.particles.components.ParticleDataSpriteComponent;
import org.terasology.particles.components.ParticleEmitterComponent;
import org.terasology.registry.In;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.items.BlockItemComponent;

@RegisterSystem(RegisterMode.AUTHORITY)
public class AttackSystem extends BaseComponentSystem {
    @In
    InventoryManager inventoryManager;
    @In
    EntityManager entityManager;
    @In
    private WorldProvider worldProvider;
    @In
    private BlockManager blockManager;

    private EntityRef flagSlot;
    private EntityRef item;


    @ReceiveEvent(components = {FlagDropOnActivateComponent.class, PlayerCharacterComponent.class, HasFlagComponent.class})
    public void onActivate(ActivateEvent event, EntityRef entity) {
        dropFlagOnPlayerAttack(event, entity);
    }

    /**
     * When player activates another with the magic staff, checks to see if the attacked player has a flag
     * If so, makes the player drop the flag
     * targetPlayer is player being attacked
     */
    private void dropFlagOnPlayerAttack(ActivateEvent event, EntityRef targetPlayer) {
        EntityRef attackingPlayer = event.getInstigator(); // The player using the staff to attack
        if (canPlayerAttack(attackingPlayer)) {
            if (targetPlayer.hasComponent(PlayerCharacterComponent.class) && targetPlayer.hasComponent(HasFlagComponent.class)) {
                // If the target player has the black flag
                if (targetPlayer.getComponent(HasFlagComponent.class).flag.equals(LASUtils.BLACK_TEAM)) {
                    dropFlag(targetPlayer, attackingPlayer, LASUtils.BLACK_FLAG_URI);
                    return;
                }
                if (targetPlayer.getComponent(HasFlagComponent.class).flag.equals(LASUtils.RED_TEAM)) {
                    dropFlag(targetPlayer, attackingPlayer, LASUtils.RED_FLAG_URI);
                    return;
                }
                removeParticleEmitterFromPlayer(targetPlayer);
            }
        }
    }

    private void removeParticleEmitterFromPlayer(EntityRef player) {
        player.removeComponent(ParticleEmitterComponent.class);
        player.removeComponent(ParticleDataSpriteComponent.class);
    }

    private void dropFlag(EntityRef targetPlayer, EntityRef attackingPlayer, String flagTeam) {
        targetPlayer.removeComponent(HasFlagComponent.class);
        int inventorySize = inventoryManager.getNumSlots(targetPlayer);
        for (int slotNumber = 0; slotNumber <= inventorySize; slotNumber++) {
            EntityRef inventorySlot = inventoryManager.getItemInSlot(targetPlayer, slotNumber);
            if (inventorySlot.hasComponent(BlockItemComponent.class)) {
                if (inventorySlot.getComponent(BlockItemComponent.class).blockFamily.getURI().toString().equals(flagTeam)) {
                    flagSlot = inventorySlot;
                }
            }
        }
        Vector3f position = new Vector3f(targetPlayer.getComponent(LocationComponent.class).getLocalPosition());
        Vector3f impulseVector = new Vector3f(attackingPlayer.getComponent(LocationComponent.class).getLocalPosition());
        targetPlayer.send(new DropItemRequest(flagSlot, targetPlayer, impulseVector, position));
    }

    private boolean canPlayerAttack(EntityRef attackingPlayer) {
        if (!attackingPlayer.hasComponent(CharacterHeldItemComponent.class)) {
            return false;
        }
        EntityRef heldItem = attackingPlayer.getComponent(CharacterHeldItemComponent.class).selectedItem;
        return heldItem.hasComponent(RaycastOnActivateComponent.class);
    }

    /**
     * Checks if player picks up flag of the same team.
     * If so, moves flag back to base
     */
    // TODO: Handle player dropping flag
    @ReceiveEvent(components = {LASTeamComponent.class})
    public void onInventorySlotChanged(InventorySlotChangedEvent event, EntityRef entity) {
        EntityRef player = entity;
        item = event.getNewItem();
        if (item.hasComponent(BlackFlagComponent.class) || item.hasComponent(RedFlagComponent.class)) {
            String flagTeam = checkWhichFlagPicked(event);
            if (flagTeam.equals(player.getComponent(LASTeamComponent.class).team)) {
                moveFlagToBase(player, flagTeam);
            } else {
                handleFlagPickup(player, flagTeam);
            }
        }
    }

    private void handleFlagPickup(EntityRef player, String flagTeam) {
        player.addComponent(new HasFlagComponent(flagTeam));
        addParticleEmitterToPlayer(player, flagTeam);
    }

    private void addParticleEmitterToPlayer(EntityRef player, String flagTeam) {
        if (flagTeam.equals(LASUtils.BLACK_TEAM)) {
            player.addOrSaveComponent(LASUtils.getSpadesParticleSprite());
        }
        if (flagTeam.equals(LASUtils.RED_TEAM)) {
            player.addOrSaveComponent(LASUtils.getHeartsParticleSprite());
        }
        player.addOrSaveComponent(LASUtils.getParticleEmitterComponent());
    }

    private void moveFlagToBase(EntityRef playerEntity, String flagTeam) {
        worldProvider.setBlock(LASUtils.getFlagLocation(flagTeam), blockManager.getBlock(LASUtils.getFlagURI(flagTeam)));
        inventoryManager.removeItem(playerEntity, EntityRef.NULL, item, true, 1);
    }

    private String checkWhichFlagPicked(InventorySlotChangedEvent event) {
        item = event.getNewItem();
        if (item.hasComponent(BlackFlagComponent.class)) {
            return LASUtils.BLACK_TEAM;
        }
        if (item.hasComponent(RedFlagComponent.class)) {
            return LASUtils.RED_TEAM;
        }
        return null;
    }
}
