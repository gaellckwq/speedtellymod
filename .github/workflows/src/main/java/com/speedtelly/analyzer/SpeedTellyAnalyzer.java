package com.speedtelly.analyzer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = "speedtellyanalyzer", name = "Speed Telly Analyzer", version = "1.0")
public class SpeedTellyAnalyzer {
    
    private static final Minecraft mc = Minecraft.getMinecraft();
    private KeyBinding toggleKey;
    private boolean enabled = false;
    private int blockCount = 0;
    private BridgeMode mode = BridgeMode.NORMAL;
    private float[] aimOffset = {0, 0};
    private int tickCounter = 0;
    private boolean wasdState = false;
    
    public enum BridgeMode {
        EXTRA_SHORT(50), SHORT(100), NORMAL(200), LONG(350), EXTRA_LONG(500), INFINITY(1000);
        
        final int maxBlocks;
        BridgeMode(int maxBlocks) {
            this.maxBlocks = maxBlocks;
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Keybinding
        toggleKey = new KeyBinding("Toggle Speed Telly", Keyboard.KEY_P, "Speed Telly Analyzer");
        ClientRegistry.registerKeyBinding(toggleKey);
        
        // Event handler
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new ModeCommand());
    }
    
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            toggle();
        }
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled || mc.thePlayer == null) return;
        
        // Safety check 1: Multiplayer detection
        if (!mc.isSingleplayer()) {
            disable();
            mc.thePlayer.addChatMessage(new ChatComponentText("§cERROR: Only works in single player!"));
            return;
        }
        
        // Safety check 2: Block limit
        if (blockCount >= mode.maxBlocks) {
            disable();
            mc.thePlayer.addChatMessage(new ChatComponentText("§cBlock limit reached (" + mode.maxBlocks + ")"));
            return;
        }
        
        tickCounter++;
        
        // Auto Aim (mengkompensasi drift)
        autoAim();
        
        // WASD Rhythm Engine
        executeWASDRhythm();
        
        // Auto Click Engine
        executeAutoClick();
        
        // Visual feedback
        if (tickCounter % 20 == 0) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§eBlocks: " + blockCount + " | Mode: " + mode));
        }
    }
    
    private void autoAim() {
        // Hitung drift berdasarkan gerakan pemain
        float driftX = mc.thePlayer.motionX * 50;
        float driftZ = mc.thePlayer.motionZ * 50;
        
        // Kompensasi dengan micro mouse movement
        if (Math.abs(driftX) > 0.1) {
            aimOffset[0] -= driftX * 0.1f;
        }
        if (Math.abs(driftZ) > 0.1) {
            aimOffset[1] -= driftZ * 0.1f;
        }
        
        // Terapkan koreksi aim
        mc.thePlayer.rotationYaw += aimOffset[0];
        mc.thePlayer.rotationPitch += aimOffset[1];
        
        // Reset offset secara bertahap
        aimOffset[0] *= 0.9f;
        aimOffset[1] *= 0.9f;
    }
    
    private void executeWASDRhythm() {
        // Pola WASD rhythm berdasarkan mode
        int rhythmSpeed = mode.ordinal() + 1; // Semakin panjang mode, semakin cepat
        
        if (tickCounter % (4 - rhythmSpeed) == 0) {
            if (!wasdState) {
                // Tekan W
                mc.gameSettings.keyBindForward.pressed = true;
                mc.gameSettings.keyBindLeft.pressed = false;
                mc.gameSettings.keyBindRight.pressed = false;
            } else {
                // Tekan A/D bergantian
                boolean pressLeft = (tickCounter % 8 == 0);
                mc.gameSettings.keyBindForward.pressed = false;
                mc.gameSettings.keyBindLeft.pressed = pressLeft;
                mc.gameSettings.keyBindRight.pressed = !pressLeft;
            }
            wasdState = !wasdState;
        }
    }
    
    private void executeAutoClick() {
        // CPS berdasarkan mode (8-20 CPS)
        int cps = 8 + (mode.ordinal() * 2);
        int clickDelay = 20 / cps;
        
        if (tickCounter % clickDelay == 0) {
            if (mc.thePlayer.getHeldItem() != null) {
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
                blockCount++;
            }
        }
    }
    
    private void toggle() {
        enabled = !enabled;
        if (enabled) {
            blockCount = 0;
            tickCounter = 0;
            mc.thePlayer.addChatMessage(new ChatComponentText("§aSpeed Telly Analyzer ENABLED (" + mode + ")"));
        } else {
            resetKeys();
            mc.thePlayer.addChatMessage(new ChatComponentText("§cSpeed Telly Analyzer DISABLED"));
        }
    }
    
    private void disable() {
        enabled = false;
        resetKeys();
    }
    
    private void resetKeys() {
        mc.gameSettings.keyBindForward.pressed = false;
        mc.gameSettings.keyBindLeft.pressed = false;
        mc.gameSettings.keyBindRight.pressed = false;
    }
    
    public static class ModeCommand extends net.minecraft.command.CommandBase {
        @Override
        public String getCommandName() {
            return "stanalyzer";
        }
        
        @Override
        public String getCommandUsage(net.minecraft.command.ICommandSender sender) {
            return "/stanalyzer <mode>";
        }
        
        @Override
        public void processCommand(net.minecraft.command.ICommandSender sender, String[] args) {
            if (args.length == 0) {
                sender.addChatMessage(new ChatComponentText("§eAvailable modes: EXTRA_SHORT, SHORT, NORMAL, LONG, EXTRA_LONG, INFINITY"));
                return;
            }
            
            try {
                BridgeMode newMode = BridgeMode.valueOf(args[0].toUpperCase());
                SpeedTellyAnalyzer analyzer = (SpeedTellyAnalyzer) MinecraftForge.EVENT_BUS.findFirstTargetForEventBusFamily(SpeedTellyAnalyzer.class);
                if (analyzer != null) {
                    analyzer.mode = newMode;
                    sender.addChatMessage(new ChatComponentText("§aMode set to: " + newMode));
                }
            } catch (IllegalArgumentException e) {
                sender.addChatMessage(new ChatComponentText("§cInvalid mode! Use: /stanalyzer <mode>"));
            }
        }
        
        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }
}
