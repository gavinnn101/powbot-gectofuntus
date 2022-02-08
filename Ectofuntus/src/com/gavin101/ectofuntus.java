package com.gavin101;

import com.android.tools.r8.graph.S;
import com.android.tools.r8.graph.T;
import com.google.common.eventbus.Subscribe;
import org.powbot.api.*;
import org.powbot.api.event.MessageEvent;
import org.powbot.api.rt4.*;
import org.powbot.api.rt4.walking.model.Skill;
import org.powbot.api.script.AbstractScript;
import org.powbot.api.script.ScriptManifest;
import org.powbot.api.script.paint.Paint;
import org.powbot.api.script.paint.PaintBuilder;
import org.powbot.mobile.script.ScriptManager;
import org.powbot.mobile.service.ScriptUploader;


@ScriptManifest(
        name = "GEctofuntus",
        description = "Levels prayer via Ectofuntus.",
        version = "0.0.1"
)

public class ectofuntus extends AbstractScript {
    String currentState = "Starting";
    boolean needSlime = true;

    Area ECTO_AREA = new Area(new Tile(3654, 3525), new Tile(3664, 3515));
    Area BANK_AREA = new Area(new Tile(3686, 3471), new Tile(3691, 3467));
    Tile SLIME_TILE = new Tile(3683, 9888, 0);
    // floor 3
    Area SLIME_FLOOR_3 = new Area(new Tile(3670, 9901, 1), new Tile(3687, 9877, 1));
    Area SLIME_FLOOR_2 = new Area(new Tile(3668, 9903, 2), new Tile(3693, 9874, 2));
    Area SLIME_FLOOR_1 = new Area(new Tile(3668, 9903, 3), new Tile(3693, 9874, 3));

    Area PORT_PHASMATYS = new Area(new Tile(3654, 4506, 0), new Tile(3704, 3457, 0));

    public static int BANKBOOTH_ID = 16642;
    public static int FULL_ECTOPHIAL_ID = 4251;
    public static int EMPTY_ECTOPHIAL_ID = 4252;
    public static int BARRIER_ID = 16105;
    public boolean barrierFlag = false;


    public static void main(String[] args) {
        new ScriptUploader().uploadAndStart("GEctofuntus", "hcim", "powbot", false, true);
    }

    public boolean needToBank() {
        return (Inventory.stream().name("Bucket").isEmpty() && (Players.local().animation() != Players.local().movementAnimation()));
    }

    public void bankSlime() {
        if (!PORT_PHASMATYS.contains(Players.local().tile()) && !ECTO_AREA.contains(Players.local().tile())) {
            Condition.wait(this::useEctophial, 250, 20);
        }
//        GameObject barrier = Objects.stream().id(BARRIER_ID).nearest().first();
//        if (!barrier.valid()) {
//            return;
//        }
//        if (!barrier.inViewport()) {
//            state("Turning camera to barrier");
//            Camera.turnTo(barrier);
//            Condition.wait(barrier::inViewport, 250, 20);
//        }
//        state("Interacting with barrier");
//        barrier.interact("Pass");
//        if (Condition.wait(Chat::canContinue, 500, 20)) {
//            state("Clicking continue on barrier dialog");
//            Chat.clickContinue();
//            Condition.wait(() -> PORT_PHASMATYS.contains(Players.local().tile()), 250, 20);
//        }
        if (!BANK_AREA.contains(Players.local().tile()) && (Players.local().animation() != Players.local().movementAnimation())) {
            state("Moving to bank");
            Movement.walkTo(BANK_AREA.getRandomTile());
            Condition.wait(() -> BANK_AREA.contains(Players.local().tile()), 2000, 20);
        }
        GameObject bankBooth = Objects.stream().id(BANKBOOTH_ID).nearest().first();
        if (!bankBooth.valid()) {
            return;
        }
        if (!bankBooth.inViewport()) {
            state("Turning camera to bank booth");
            Camera.turnTo(bankBooth);
        }
        state("Opening bank");
        if (Bank.open()) {
            state("Depositing items");
            Bank.depositAllExcept(FULL_ECTOPHIAL_ID);
            Condition.wait(() -> Inventory.occupiedSlotCount() <= 1, 250, 20);
            state("Checking for buckets");
            var bucketsInBank = Bank.stream().name("Bucket").first();
            if (!bucketsInBank.valid() || bucketsInBank.stackSize() == 0) {
                System.out.println("Out of buckets. Stopping script.");
                ScriptManager.INSTANCE.stop();
            }
            state("Withdrawing buckets");
            Bank.withdraw("Bucket", Bank.Amount.ALL);
            Condition.wait(() -> Inventory.stream().name("Bucket").isNotEmpty(), 250, 50);
            state("Closing bank");
            Bank.close();
            Condition.wait(() -> !Bank.opened(), 250, 20);
        }
    }

    public boolean useEctophial() {
        Item ectophial = Inventory.stream().id(FULL_ECTOPHIAL_ID).first();
        if (!ectophial.valid()) {
            return false;
        }
        state("Using ectophial");
        ectophial.interact("Empty");
        state("Waiting for ectophial to refill");
        return Condition.wait(() -> (ECTO_AREA.contains(Players.local().tile()) && Inventory.stream().id(FULL_ECTOPHIAL_ID).isNotEmpty()), 500, 20);
    }

    public void collectSlime() {
        if (!ECTO_AREA.contains(Players.local().tile())) {
            if (Game.tab(Game.Tab.INVENTORY)) {
                if (!useEctophial()) {
                    Movement.walkTo(ECTO_AREA.getRandomTile());
                    Condition.wait(() -> ECTO_AREA.contains(Players.local().tile()), 500, 20);
                }
            }
        }
        Movement.walkTo(SLIME_TILE);
        Condition.wait(() -> Players.local().tile() == SLIME_TILE, 500, 20);
    }

    public void cameraCheck() {
//        System.out.println("Zoom: " +Camera.getZoom());
        if (Camera.getZoom() >= 50) {
            state("Zooming camera out");
            Camera.moveZoomSlider(9);
        }
        if (Camera.pitch() < 90) {
            state("Changing camera angle");
            Camera.pitch(true);
        }
    }

    @Override
    public void poll() {
        cameraCheck();
        if (needSlime) {
            if (needToBank()) {
                bankSlime();
            } else {
                collectSlime();
            }
        } else {
            // leveling prayer functions here
        }
    }

    @Override
    public void onStart() {
//        Condition.sleep(1000);
        Condition.wait(() -> Players.local().valid(), 500, 50);
        state("Checking Camera");
        cameraCheck();
        System.out.println("Starting Gavin101's Ectofuntus...");

        Paint paint = new PaintBuilder().trackSkill(Skill.Prayer)
                .addString(() -> currentState)
                .build();
        addPaint(paint);
    }

    public void state(String s) {
        currentState = s;
        System.out.println(s);
    }
}
