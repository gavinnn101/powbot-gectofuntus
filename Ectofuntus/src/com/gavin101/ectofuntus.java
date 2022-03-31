package com.gavin101;

import com.google.common.eventbus.Subscribe;
import org.powbot.api.*;
import org.powbot.api.event.BreakEvent;
import org.powbot.api.event.MessageEvent;
import org.powbot.api.event.VarpbitChangedEvent;
import org.powbot.api.rt4.*;
import org.powbot.api.rt4.walking.model.Skill;
import org.powbot.api.script.AbstractScript;
import org.powbot.api.script.OptionType;
import org.powbot.api.script.ScriptConfiguration;
import org.powbot.api.script.ScriptManifest;
import org.powbot.api.script.paint.Paint;
import org.powbot.api.script.paint.PaintBuilder;
import org.powbot.mobile.script.ScriptManager;
import org.powbot.mobile.service.ScriptUploader;


import java.awt.*;
import java.util.HashMap;
import java.util.Map;


@ScriptManifest(
        name = "Gectofuntus",
        description = "Levels prayer via Ectofuntus.",
        version = "0.0.1"
)

@ScriptConfiguration.List(
        {
                @ScriptConfiguration(
                        name = "Collect Slime",
                        description = "Collects slime with the buckets in your bank.",
                        optionType = OptionType.BOOLEAN,
                        defaultValue = "false"
                ),
                @ScriptConfiguration(
                        name = "Crush bones",
                        description = "Crushes bones upstairs at the ectofuntus for bonemeal.",
                        optionType = OptionType.BOOLEAN,
                        defaultValue = "false"
                ),
                @ScriptConfiguration(
                        name = "Bones",
                        description = "Type of bones to use",
                        optionType = OptionType.STRING,
                        defaultValue = "Dragon bones",
                        allowedValues = {"Big bones", "Dragon bones", "Lava dragon bones", "Wyvern bones", "Superior dragon bones"}
                )
        }
)

public class ectofuntus extends AbstractScript {
    String currentState = "Starting";
    boolean needSlime;
    boolean needBonemeal;
    int boneCounter;

    Area ECTO_AREA = new Area(new Tile(3654, 3525), new Tile(3664, 3515));
    Area BANK_AREA = new Area(new Tile(3686, 3471), new Tile(3691, 3467));
    Tile SLIME_TILE = new Tile(3683, 9888, 0);


    Area PORT_PHASMATYS = new Area(new Tile(3659, 3507, 0), new Tile(3691, 3466, 0));
    Tile BARRIER_TILE_WEST = new Tile(3659, 3507, 0);
    Tile BARRIER_TILE_EAST = new Tile(3660, 3507, 0);

    public static int BANKBOOTH_ID = 16642;
    public static int FULL_ECTOPHIAL_ID = 4251;
    public static int EMPTY_ECTOPHIAL_ID = 4252;
    public static int BARRIER_ID = 16105;
    public static int SLIME_ID = 17119;
    public static int BUCKET_OF_SLIME_ID = 4286;

    Area LOADER_AREA = new Area(new Tile(3654, 3526, 1), new Tile(3667, 3520, 1));
    public static int LOADER_ID = 16654;
    public static int GRINDER_ID = 16655;
    public static int BIN_ID = 16656;
    String boneType;

    public static int LOADER_VARP = 408;
    public static int LOADER_EMPTY = -2017034368;
    public static int LOADER_READY = -2017001600;
    public static int LOADER_FINISHED = -2016968832;

    public Map<String, Integer> requiredItems = new HashMap<>();

    public static void main(String[] args) {
        new ScriptUploader().uploadAndStart("Gectofuntus", "gim", "powbot", false, true);
    }

    @Override
    public void onStart() {
        Condition.wait(() -> Players.local().valid(), 500, 50);
        state("Checking Camera");
        cameraCheck();
        System.out.println("Starting Gavin101's Ectofuntus...");

        Paint paint = new PaintBuilder().trackSkill(Skill.Prayer)
                .addString(() -> currentState)
                .addString(() -> "Bones offered: " +boneCounter)
                .trackInventoryItem(BUCKET_OF_SLIME_ID)
                .build();
        addPaint(paint);

        Object needSlimeObj = getOption("Collect Slime");
        Object boneTypeObj = getOption("Bones");
        needSlime = (boolean) needSlimeObj;
        boneType = boneTypeObj.toString();

//        requiredItems.put("Ectophial", 1);
        if (needSlime) {
            requiredItems.put("Bucket", 27);
        } else if (needBonemeal){
            requiredItems.put("Pot", 13);
            requiredItems.put(boneType, 13);
        } else {
            requiredItems.put("Dragon bonemeal", 13);
            requiredItems.put("Bucket of slime", 13);
        }
    }

    @Override
    public void poll() {
        cameraCheck();
        if (needSlime) {
            if (needToBank(new String[] {"bucket"})) {
                handleBanking();
            } else if (needToCollectSlime()){
                collectSlime();
            } else {
                moveToSlime();
            }
        } else if (needBonemeal) {
            if (needToBank(new String[] {"Pot"})) {
                handleBanking();
            } else if (needToCrush()) {
                crushBones();
            } else if (!LOADER_AREA.contains(Players.local().tile())){
                moveToLoader();
            }
        } else {
            if (needToBank(new String[] {"Bucket of slime", "Dragon bonemeal"})) {
                handleBanking();
            } else if (needToOffer()) {
                offerBones();
            } else if (!ECTO_AREA.contains(Players.local().tile())) {
                useEctophial();
            }
        }
    }

//    public boolean needToBank(String missingItem) {
//        state("Checking if we need to bank");
//        return (Inventory.stream().name(missingItem).isEmpty() && (Players.local().animation() != Players.local().movementAnimation()));
//    }

    public boolean needToBank(String[] missingItems) {
        state("Checking if we need to bank");
        if (Players.local().animation() == Players.local().movementAnimation()) {
            return false;
        }
        for (String missingItem : missingItems) {
            if (Inventory.stream().name(missingItem).isEmpty()) {
                return true;
            }
        }
        return false;
    }


    public void handleBanking() {
        state("Entering handleBanking()");
        if (Players.local().animation() == Players.local().movementAnimation()) {
            return;
        }
        if (BANK_AREA.contains(Players.local().tile())) {
            state("Checking for bank booth");
            GameObject bankBooth = Objects.stream().id(BANKBOOTH_ID).nearest().first();
            if (!bankBooth.valid()) {
                return;
            }
            if (!bankBooth.inViewport()) {
                state("Turning camera to bank booth");
                Camera.turnTo(Bank.nearest());
                Condition.wait(bankBooth::inViewport, 250, 10);
            }
            if (Bank.open()) {
                state("Depositing items");
                Bank.depositAllExcept(FULL_ECTOPHIAL_ID);
                Condition.wait(() -> Inventory.occupiedSlotCount() <= 1, 250, 10);
                state("Checking for needed items");
                for (var itemEntry : requiredItems.entrySet()) {
                    String item = itemEntry.getKey();
                    Integer itemQuantity = itemEntry.getValue();
                    state("Withdrawing " +itemQuantity +" " +item);
                    if (!Bank.stream().name(item).first().valid() || Bank.stream().name(item).first().stackSize() < itemQuantity) {
                        System.out.println("Out of required items. Stopping script.");
                        Game.logout();
                        Condition.wait(() -> Players.local().valid(), 500, 20);
                        ScriptManager.INSTANCE.stop();
                    } else {
                        state("Withdrawing: " +item);
                        Bank.withdraw(item, itemQuantity);
                        Condition.wait(() -> Inventory.stream().name(item).isNotEmpty(), 250, 50);
                    }
                }
                state("Closing bank");
                Bank.close();
                Condition.wait(() -> !Bank.opened(), 250, 20);
            }
        } else if (PORT_PHASMATYS.contains(Players.local().tile())) {
            state("Moving to bank");
            Movement.walkTo(BANK_AREA.getRandomTile());
            Condition.wait(() -> BANK_AREA.contains(Players.local().tile()), 500, 20);
        } else {
            if (!ECTO_AREA.contains(Players.local().tile())) {
                state("Using ectophial - bank debug");
                Condition.wait(this::useEctophial, 250, 20);
            }
            enterBarrier();
            Condition.wait(() -> PORT_PHASMATYS.contains(Players.local().tile()), 250, 10);
        }
    }

    public void enterBarrier() {
        GameObject barrier = Objects.stream().id(BARRIER_ID).nearest().first();
        if (!barrier.valid()) {
            return;
        }
        if (!barrier.inViewport()) {
            state("Turning camera to barrier");
            Camera.turnTo(barrier);
            Condition.wait(barrier::inViewport, 250, 20);
        }
        state("Interacting with barrier");
        barrier.interact("Pass");
        if (Condition.wait(Chat::canContinue, 500, 20)) {
            state("Clicking continue on barrier dialog");
            Chat.clickContinue();
            state("Waiting to get inside");
            Condition.wait(() -> Players.local().tile().equals(BARRIER_TILE_WEST) || Players.local().tile().equals(BARRIER_TILE_EAST), 250, 20);
        }
    }

    public boolean useEctophial() {
        Item ectophial = Inventory.stream().id(FULL_ECTOPHIAL_ID).first();
        if (!ectophial.valid()) {
            return false;
        }
        state("Using ectophial");
        if (Game.tab(Game.Tab.INVENTORY)) {
            ectophial.interact("Empty");
            state("Waiting for ectophial to refill");
            return Condition.wait(() -> (ECTO_AREA.contains(Players.local().tile()) && Inventory.stream().id(FULL_ECTOPHIAL_ID).isNotEmpty()), 500, 20);
        }
        return false;
    }

    public void moveToSlime() {
        // Moves character to slime collection tile
        state("Entered moveToSlime()");
        if (Players.local().animation() == Players.local().movementAnimation()) {
            return;
        }
        if (!ECTO_AREA.contains(Players.local().tile()) && !Players.local().tile().equals(SLIME_TILE)) {
            if (!useEctophial()) {
                state("Walking to Ecto token collection area");
                Movement.walkTo(ECTO_AREA.getRandomTile());
                Condition.wait(() -> ECTO_AREA.contains(Players.local().tile()), 500, 20);
            }
        }
        if (ECTO_AREA.contains(Players.local().tile())) {
            state("Walking to slime collection tile");
            Movement.walkTo(SLIME_TILE);
            Condition.wait(() -> Players.local().tile().equals(SLIME_TILE), 500, 20);
        }
    }

    public boolean needToCollectSlime() {
        state("Checking if we need to collect slime");
        return (Players.local().tile().equals(SLIME_TILE) && Inventory.stream().name("Bucket").isNotEmpty());
    }

    public void collectSlime() {
        state("Entering collectSlime()");
        GameObject slime = Objects.stream(2).id(SLIME_ID).first();
        if (!Game.tab(Game.Tab.INVENTORY)) {
            Condition.wait(() -> Game.tab(Game.Tab.INVENTORY), 250, 20);
        }
        if (Inventory.stream().name("Bucket").isNotEmpty()) {
            Item bucket = Inventory.stream().name("Bucket").first();
            if (Inventory.selectedItem().id() == -1) {
                state("Clicking bucket");
                bucket.interact("Use");
            } else if (Inventory.selectedItem().id() == bucket.id()) {
                state("Using bucket on slime pool");
                slime.interact("Use Bucket -> Pool of Slime");
                state("Waiting to finish filling buckets");
                Condition.wait(() -> Inventory.stream().name("Bucket").isEmpty(), 5_000, 15);
            }
        }
    }

    public boolean needToCrush() {
        state("Checking if we need to crush bones");
        return (Inventory.stream().name(boneType).isNotEmpty() && LOADER_AREA.contains(Players.local().tile()) && Players.local().animation() == -1);
//                || ((Varpbits.varpbit(LOADER_VARP) == LOADER_READY) && Inventory.stream().name("Pot").isNotEmpty()) && LOADER_AREA.contains(Players.local().tile()) && Players.local().animation() == -1;
    }

    public void crushBones() {
        state("Entering crushBones()");
        GameObject loader = Objects.stream().id(LOADER_ID).nearest().first();
        GameObject grinder = Objects.stream().id(GRINDER_ID).nearest().first();
        GameObject bin = Objects.stream().id(BIN_ID).nearest().first();
        if (!Game.tab(Game.Tab.INVENTORY)) {
            Condition.wait(() -> Game.tab(Game.Tab.INVENTORY), 250, 20);
        }
        if (!grinder.inViewport()) {
            state("Turning camera to grinder");
            Camera.turnTo(grinder);
            Condition.wait(grinder::inViewport, 250, 10);
        }
        if (Varpbits.varpbit(LOADER_VARP) == LOADER_EMPTY && Players.local().animation() == -1) {
            if (Inventory.stream().name(boneType).isNotEmpty()) {
                Item bones = Inventory.stream().name(boneType).first();
                if (Inventory.selectedItem().id() == -1) {
                    state("Clicking bones");
                    if (bones.interact("Use")) {
                        Condition.wait(() -> Inventory.selectedItem().id() == bones.id(), 250, 20);
                        state("Using bones on hopper");
                        loader.interact("Use " + boneType + " -> Loader");
                        state("Waiting to finish crushing bones");
                        Condition.wait(() -> (Varpbits.varpbit(LOADER_VARP) == LOADER_READY), 300, 10);
                    }
                }
            }
        }
        if (Varpbits.varpbit(LOADER_VARP) == LOADER_READY && Players.local().animation() == -1) {
            state("Winding the bone grinder");
            grinder.interact("Wind");
            Condition.wait(() -> Varpbits.varpbit(LOADER_VARP) == LOADER_FINISHED, 300, 10);
        }
        if (Varpbits.varpbit(LOADER_VARP) == LOADER_FINISHED && Players.local().animation() == -1) {
            state("Emptying the bin");
            bin.interact("Empty");
            Condition.wait(() -> Varpbits.varpbit(LOADER_VARP) == LOADER_EMPTY, 300, 10);
        }
    }

    public void moveToLoader() {
        state("Entering moveToLoader");
        if (Players.local().animation() != -1) {
            return;
        }
        if (!LOADER_AREA.contains(Players.local().tile()) && !ECTO_AREA.contains(Players.local().tile())) {
            state("Using ectophial - moveToLoader()");
            Condition.wait(this::useEctophial, 250, 20);
            Condition.wait(() -> ECTO_AREA.contains(Players.local().tile()), 250, 10);
        }
        state("Moving to loader");
        Movement.moveTo(LOADER_AREA.getRandomTile());
        Condition.wait(() -> LOADER_AREA.contains(Players.local().tile()), 500, 20);
    }

    public boolean needToOffer() {
        return (ECTO_AREA.contains(Players.local().tile()) && Inventory.stream().name("Bucket of slime").isNotEmpty() && Inventory.stream().name("Dragon bonemeal").isNotEmpty());
    }

    public void offerBones() {
        state("Entering offerBones()");
        System.out.println("Message: " +Chat.stream().textContains("in.").isNotEmpty());
        if (needToCollectTokens()) {
            collectTokens();
        }
        GameObject ectofuntus = Objects.stream().name("Ectofuntus").nearest().first();
        if (!ectofuntus.valid()) {
            return;
        }
        if (!ectofuntus.inViewport()) {
            state("Turning camera to ectofuntus");
            Camera.turnTo(ectofuntus);
            Condition.wait(ectofuntus::inViewport, 250, 10);
        }
        state("Worshipping ectofuntus");
        long bucketCount = Inventory.stream().name("Bucket of slime").count();
        ectofuntus.interact("Worship");
        Condition.wait(() -> Inventory.stream().name("Bucket of slime").count() < bucketCount, 100, 10);
    }

    public boolean needToCollectTokens() {
        return Chat.stream().textContains("There isn't").isNotEmpty();
//        System.out.println(Chat.stream());
//        return  (Chat.stream().textContains("There isn't room").isNotEmpty());
//        return Chat.canContinue();
    }

    public void collectTokens() {
        System.out.println("Chat message: "+Chat.getChatMessage().contains("There isn't"));
        state("Collecting tokens");
        Npc ghost = Npcs.stream().name("Ghost disciple").nearest().first();
        if (!ghost.valid()) {
            return;
        }
        if (!ghost.inViewport()) {
            state("Turning camera to ghost disciple");
            Camera.turnTo(ghost);
            Condition.wait(ghost::inViewport, 250, 10);
        }
        state("Talking to ghost");
        ghost.interact("Talk-to");
        if (Condition.wait(Chat::canContinue, 100, 10)) {
            Chat.clickContinue();
            Condition.wait(() -> !Chat.canContinue(), 100, 20);
        }
    }

//    @Subscribe
//    public void onVarpChange(VarpbitChangedEvent e) {
//        System.out.println("varp change: " +e);
//    }

    @Subscribe
    public void onMessage(MessageEvent e) {
        String text = e.getMessage();
        if (text.contains("You fill a pot")) {
            boneCounter++;
        }
    }

    @Subscribe
    public void onBreak(BreakEvent e) {
        if(Bank.opened() || Varpbits.varpbit(LOADER_VARP) == LOADER_READY) {
            e.delay(500);
        }
    }


    public void cameraCheck() {
//        System.out.println("Zoom: " +Camera.getZoom());
        if (Camera.getZoom() >= 10) {
            state("Zooming camera out");
            Camera.moveZoomSlider(9);
        }
        if (Camera.pitch() < 90) {
            state("Changing camera angle");
            Camera.pitch(true);
        }
    }


    public void state(String s) {
        currentState = s;
        System.out.println(s);
    }
}


