package com.heledron.hologram.disc_player

import com.heledron.hologram.utilities.events.addEventListener
import com.heledron.hologram.utilities.events.onTick
import com.heledron.hologram.utilities.custom_items.customItemRegistry
import com.heledron.hologram.utilities.currentPlugin
import org.bukkit.Material
import org.bukkit.block.Jukebox
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.EventPriority

fun setupDiscPlayer() {
    // Load configuration
    VideoDiscConfig.load()
    
    val videoPlayer = VideoPlayer()
    
    // Add custom music disc to items registry if enabled
    if (VideoDiscConfig.useCustomDisc) {
        val disc = Disc.createDisc()
        customItemRegistry.add(disc)
        println("Video music disc added to items registry")
    }
    
    // Listen for disc insertion into jukebox
    addEventListener(object : org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
        fun onJukeboxInteract(event: PlayerInteractEvent) {
            val player = event.player
            val itemInHand = player.inventory.itemInMainHand
            val clickedBlock = event.clickedBlock
            
            // Check if clicking on a jukebox with music disc
            if (event.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK &&
                clickedBlock?.type == Material.JUKEBOX &&
                Disc.isDisc(itemInHand)) {
                
                // Let the vanilla jukebox handle the disc insertion
                // We'll check for the disc in our update loop
                player.sendMessage("§aVideo disc inserted! Video will start playing above the jukebox.")
                
                // Schedule a check after the disc is inserted
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                    currentPlugin,
                    java.lang.Runnable {
                        val jukebox = clickedBlock.state as? Jukebox
                        if (jukebox != null) {
                            // Check if jukebox is playing our disc (custom or regular if accepted)
                            val isPlayingOurDisc = jukebox.playing == Material.MUSIC_DISC_BLOCKS && 
                                (jukebox.inventory.any { itemStack -> 
                                    itemStack != null && Disc.isDisc(itemStack) 
                                } || VideoDiscConfig.acceptRegularDiscs)
                            
                            if (isPlayingOurDisc) {
                                videoPlayer.startJukeboxVideo(jukebox)
                            }
                        }
                    },
                    2L // Check after 2 ticks to ensure the disc is inserted
                )
            }
        }
    })
    
    // Listen for jukebox breaking to stop video
    addEventListener(object : org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
        fun onJukeboxBreak(event: BlockBreakEvent) {
            if (event.block.type == Material.JUKEBOX) {
                val jukebox = event.block.state as? Jukebox
                if (jukebox != null) {
                    videoPlayer.stopJukeboxVideo(jukebox)
                }
            }
        }
    })
    
    // Listen for disc ejection (when jukebox is clicked without a music disc in hand)
    addEventListener(object : org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
        fun onJukeboxEject(event: PlayerInteractEvent) {
            val player = event.player
            val clickedBlock = event.clickedBlock
            
            // Check if clicking on a jukebox without a music disc in hand (ejecting)
            if (event.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK &&
                clickedBlock?.type == Material.JUKEBOX &&
                !Disc.isDisc(player.inventory.itemInMainHand)) {
                
                // Schedule a check after the disc is ejected
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                    currentPlugin,
                    java.lang.Runnable {
                        val jukebox = clickedBlock.state as? Jukebox
                        if (jukebox != null && jukebox.playing != Material.MUSIC_DISC_BLOCKS) {
                            videoPlayer.stopJukeboxVideo(jukebox)
                        }
                    },
                    2L // Check after 2 ticks to ensure the disc is ejected
                )
            }
        }
    })
    
    // Listen for inventory move events to detect automatic disc insertion by droppers
    addEventListener(object : org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
        fun onInventoryMoveItem(event: org.bukkit.event.inventory.InventoryMoveItemEvent) {
            // Only process if dropper detection is enabled
            if (!VideoDiscConfig.detectDropperInsertion) return
            
            val source = event.source
            val destination = event.destination
            val item = event.item
            
            // Check if an item is being moved from a dropper to a jukebox
            if (source.holder is org.bukkit.block.Dropper && 
                destination.holder is Jukebox &&
                (Disc.isDisc(item) || (VideoDiscConfig.acceptRegularDiscs && item.type == Material.MUSIC_DISC_BLOCKS))) {
                
                println("=== INVENTORY MOVE DEBUG (VideoPlayer) ===")
                println("Item moved from dropper to jukebox: ${item.type}")
                println("Source: ${source.holder}")
                println("Destination: ${destination.holder}")
                
                val jukebox = destination.holder as Jukebox
                println("Jukebox location: ${jukebox.location}")
                println("Jukebox playing: ${jukebox.playing}")
                println("Jukebox has record: ${jukebox.hasRecord()}")
                println("Jukebox is playing: ${jukebox.isPlaying()}")
                println("Jukebox inventory contents:")
                jukebox.inventory.forEachIndexed { index, itemStack ->
                    if (itemStack != null) {
                        println("  Slot $index: ${itemStack.type}")
                    }
                }
                
                // Schedule a check after the item is moved
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                    currentPlugin,
                    java.lang.Runnable {
                        println("=== CHECKING AFTER MOVE (VideoPlayer) ===")
                        println("Jukebox playing: ${jukebox.playing}")
                        println("Jukebox has record: ${jukebox.hasRecord()}")
                        println("Jukebox is playing: ${jukebox.isPlaying()}")
                        println("Jukebox inventory contents after move:")
                        jukebox.inventory.forEachIndexed { index, itemStack ->
                            if (itemStack != null) {
                                println("  Slot $index: ${itemStack.type}")
                            }
                        }
                        
                        // Check if jukebox inventory contains our disc and is playing
                        val hasOurDisc = jukebox.inventory.any { itemStack ->
                            itemStack != null && (Disc.isDisc(itemStack) || 
                                (VideoDiscConfig.acceptRegularDiscs && itemStack.type == Material.MUSIC_DISC_BLOCKS))
                        }
                        
                        if (hasOurDisc && jukebox.isPlaying()) {
                            videoPlayer.startJukeboxVideo(jukebox)
                            println("Dropper inserted video disc into jukebox at ${jukebox.location}")
                        } else {
                            println("Jukebox does not have our disc or is not playing. Has disc: $hasOurDisc, is playing: ${jukebox.isPlaying()}")
                        }
                        println("=== CHECKING AFTER MOVE END (VideoPlayer) ===")
                    },
                    2L // Check after 2 ticks to ensure the item is moved
                )
            }
        }
    })
    
    // Update the video player every tick
    onTick {
        videoPlayer.update()
    }
} 