package com.gix.conradchallenges;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Types for the challenge function tool (MythicDungeons-style script nodes).
 */
public final class ChallengeScriptTypes {

    private ChallengeScriptTypes() {}

    /** Trigger type for challenge script nodes (mirrors MythicDungeons-style). */
    public enum ScriptTrigger {
        BLOCK_INTERACT,
        BLOCK_BREAK,
        BLOCK_PLACE,
        PLAYER_ENTER_AREA,
        MOB_DEATH,
        /** Fires when a SIGNAL_SENDER with matching signal name runs in this challenge. */
        SIGNAL_RECEIVER,
        /** Fires when the block at this location receives redstone power. */
        REDSTONE_RECEIVER,
        /** Fires when all dependency block triggers have fired this run. */
        AND_GATE,
        /** Fires when any dependency block trigger has fired this run. */
        OR_GATE
    }

    /** Function type for script nodes. No SPAWN_MOB; challenge spawn is handled by setdestination. */
    public enum ScriptFunctionType {
        SEND_MESSAGE,
        BROADCAST_MESSAGE,
        PLAY_SOUND,
        RUN_COMMAND,
        TELEPORT_PLAYER,
        /** Invisible wall: block can be walked through when a challenger is near. No trigger. */
        PASS_THROUGH,
        /** Exits the triggering player from the challenge (like /exit). */
        LEAVE_CHALLENGE,
        /** Sends a signal; SIGNAL_RECEIVER nodes with matching signal name will fire. */
        SIGNAL_SENDER,
        /** Right-click with key item on this block or any linked block to remove them (+ consume items). Stays gone until regen. */
        REMOVE_BLOCK_WHEN_ITEM_NEAR
    }

    /** One script node: a block/location + trigger + function + options (and optional conditions). */
    public static class ChallengeScriptNode {
        public final String worldName;
        public final int blockX, blockY, blockZ;
        public ScriptTrigger trigger;
        public ScriptFunctionType functionType;
        public final Map<String, Object> functionData;
        public final List<Map<String, Object>> conditions;

        public ChallengeScriptNode(String worldName, int blockX, int blockY, int blockZ) {
            this.worldName = worldName;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.trigger = ScriptTrigger.BLOCK_INTERACT;
            this.functionType = ScriptFunctionType.SEND_MESSAGE;
            this.functionData = new HashMap<>();
            this.conditions = new ArrayList<>();
        }

        public String blockKey() {
            return worldName + ":" + blockX + "," + blockY + "," + blockZ;
        }

        public static ChallengeScriptNode fromBlock(Block block) {
            return new ChallengeScriptNode(
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
        }
    }

    /** Step in the script creation/edit GUI flow. */
    public enum ScriptGuiStep {
        FUNCTION_TYPE,
        TRIGGER,
        CONFIGURE,
        /** Waiting for chat input (message, command, signal name, etc.). */
        CONFIGURE_AWAIT_CHAT,
        CONFIRM,
        /** First screen when block already has functions: Add vs Edit/Remove. */
        BLOCK_MENU,
        /** List of existing functions on this block to edit or remove. */
        EDIT_LIST,
        /** Chose one entry: Edit or Remove. */
        EDIT_OR_REMOVE,
        /** Confirm remove one function. */
        REMOVE_CONFIRM,
        /** Awaiting block click to add to linked blocks (item gate). */
        LINK_BLOCKS_AWAIT_CLICK,
        /** Menu after linking a block: Link another / Finish / Unlink block. */
        LINK_BLOCKS_MENU,
        /** Closed GUI; waiting for player to right-click with key item in hand to set it. */
        CONFIGURE_AWAIT_KEY_ITEM
    }

    /** Context for a player in the script creation/edit GUI flow. */
    public static class ScriptGuiContext {
        public final String challengeId;
        public final String worldName;
        public final int blockX, blockY, blockZ;
        public final ChallengeScriptNode node;
        public ScriptGuiStep step;
        /** When editing: index into scriptNodes list; -1 when adding new. */
        public int editingNodeIndex;
        /** When CONFIGURE_AWAIT_CHAT: which field we're setting (message, command, signal, etc.). */
        public String awaitingField;
        /** For EDIT_LIST: script node indices at this block, and -1 for pass-through. */
        public List<Integer> blockEditList;
        /** For EDIT_OR_REMOVE: index into blockEditList that was clicked. */
        public int editListClickedIndex;

        public ScriptGuiContext(String challengeId, ChallengeScriptNode node) {
            this.challengeId = challengeId;
            this.worldName = node.worldName;
            this.blockX = node.blockX;
            this.blockY = node.blockY;
            this.blockZ = node.blockZ;
            this.node = node;
            this.step = ScriptGuiStep.FUNCTION_TYPE;
            this.editingNodeIndex = -1;
            this.awaitingField = null;
            this.blockEditList = null;
            this.editListClickedIndex = -1;
        }
    }
}
