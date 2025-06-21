package com.heledron.hologram.disc_player

import com.heledron.hologram.utilities.currentTick
import com.heledron.hologram.utilities.events.onTick
import com.heledron.hologram.utilities.events.addEventListener
import com.heledron.hologram.utilities.images.sampleColor
import com.heledron.hologram.utilities.images.resize
import com.heledron.hologram.utilities.rendering.RenderGroup
import com.heledron.hologram.utilities.rendering.interpolateTransform
import com.heledron.hologram.utilities.rendering.renderText
import com.heledron.hologram.utilities.rendering.textDisplayUnitSquare
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.Jukebox
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDispenseEvent
import org.joml.Matrix4f
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.bukkit.inventory.ItemStack
import com.heledron.hologram.utilities.custom_items.customItemRegistry
import com.heledron.hologram.utilities.currentPlugin

class VideoPlayer {
    private var videoFrames = mutableListOf<BufferedImage>()
    private var frameRate = VideoDiscConfig.frameRate
    private var videoWidth = VideoDiscConfig.videoWidth
    private var videoHeight = VideoDiscConfig.videoHeight
    private var scale = VideoDiscConfig.scale
    
    // Track jukeboxes playing videos
    private val playingJukeboxes = mutableMapOf<Vector, JukeboxData>()
    
    data class JukeboxData(
        val world: World,
        val jukebox: Jukebox,
        val startTick: Long,
        val isCustomDisc: Boolean,
        var displayGroup: RenderGroup = RenderGroup()
    )
    
    fun loadVideo(videoPath: String) {
        val videoDir = File(videoPath)
        if (!videoDir.exists() || !videoDir.isDirectory) {
            println("Video directory not found: $videoPath")
            return
        }
        
        videoFrames.clear()
        val frameFiles = videoDir.listFiles { file -> 
            file.isFile && file.extension.lowercase() in VideoDiscConfig.supportedFormats
        }?.sortedBy { it.name } ?: emptyList()
        
        for (frameFile in frameFiles) {
            try {
                val frame = ImageIO.read(frameFile)
                val resizedFrame = frame.resize(videoWidth, videoHeight)
                videoFrames.add(resizedFrame)
            } catch (e: Exception) {
                println("Failed to load frame: ${frameFile.name}")
            }
        }
        
        println("Loaded ${videoFrames.size} frames")
    }
    
    fun startJukeboxVideo(jukebox: Jukebox) {
        val position = jukebox.location.toVector()
        val world = jukebox.world
        
        // Check if this jukebox is already playing
        if (playingJukeboxes.containsKey(position)) {
            return
        }
        
        // Load video if not already loaded
        if (videoFrames.isEmpty()) {
            val videoPath = VideoDiscConfig.framesDirectory
            loadVideo(videoPath)
        }
        
        val jukeboxData = JukeboxData(
            world = world,
            jukebox = jukebox,
            startTick = currentTick.toLong(),
            isCustomDisc = true // Since this method is only called for our custom disc
        )
        
        playingJukeboxes[position] = jukeboxData
        
        // Play a special sound to indicate activation
        world.playSound(jukebox.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f)
        
        println("Started video at jukebox: ${jukebox.location}")
    }

    fun stopJukeboxVideo(jukebox: Jukebox) {
        val position = jukebox.location.toVector()
        val jukeboxData = playingJukeboxes.remove(position)
        
        if (jukeboxData != null) {
            // Create a new empty display group to clear the previous one
            jukeboxData.displayGroup = RenderGroup()
            println("Stopped video at jukebox: ${jukebox.location}")
        }
    }
    
    fun hasFrames(): Boolean {
        return videoFrames.isNotEmpty()
    }
    
    private fun isDisc(item: ItemStack?): Boolean {
        if (item == null) return false
        return if (VideoDiscConfig.useCustomDisc) {
            Disc.isDisc(item)
        } else {
            item.type == Material.MUSIC_DISC_BLOCKS
        }
    }
    
    private fun isDisc(material: Material?): Boolean {
        if (material == null) return false
        return if (VideoDiscConfig.useCustomDisc) {
            material == Material.MUSIC_DISC_BLOCKS
        } else {
            material == Material.MUSIC_DISC_BLOCKS
        }
    }
    
    fun update() {
        if (videoFrames.isEmpty()) return
        
        val currentTime = currentTick.toLong()
        
        // Update each playing jukebox
        val jukeboxesToRemove = mutableListOf<Vector>()
        
        for ((position, jukeboxData) in playingJukeboxes) {
            // Check if jukebox still exists and is still playing the disc
            val block = jukeboxData.world.getBlockAt(position.toLocation(jukeboxData.world))
            if (block.type != Material.JUKEBOX) {
                jukeboxesToRemove.add(position)
                continue
            }
            
            val jukebox = block.state as? Jukebox
            if (jukebox == null || !jukeboxData.isCustomDisc) {
                jukeboxesToRemove.add(position)
                continue
            }
            
            // Calculate current frame based on time since start
            val timeSinceStart = currentTime - jukeboxData.startTick
            val totalFrames = videoFrames.size
            val framesPerSecond = frameRate.toFloat()
            val ticksPerSecond = 20.0f
            val totalVideoDuration = (totalFrames / framesPerSecond * ticksPerSecond).toLong()
            
            // Check if video has finished
            if (!VideoDiscConfig.loop && timeSinceStart >= totalVideoDuration) {
                // Video has finished, stop it
                jukeboxesToRemove.add(position)
                println("Video finished at jukebox: ${jukebox.location}")
                continue
            }
            
            // Calculate current frame (with looping if enabled)
            val currentFrame = if (VideoDiscConfig.loop) {
                ((timeSinceStart * frameRate / 20) % totalFrames).toInt()
            } else {
                ((timeSinceStart * frameRate / 20).toInt()).coerceAtMost(totalFrames - 1)
            }
            
            renderFrameForJukebox(jukeboxData, currentFrame)
        }
        
        // Remove stopped jukeboxes
        for (position in jukeboxesToRemove) {
            val jukeboxData = playingJukeboxes.remove(position)
            jukeboxData?.displayGroup = RenderGroup()
        }
    }
    
    private fun renderFrameForJukebox(jukeboxData: JukeboxData, currentFrame: Int) {
        if (currentFrame >= videoFrames.size) return
        
        val frame = videoFrames[currentFrame]
        val world = jukeboxData.world
        val jukeboxLocation = jukeboxData.jukebox.location
        
        // Position video above the jukebox
        val videoPosition = jukeboxLocation.toVector().add(
            Vector(
                VideoDiscConfig.xOffset, 
                VideoDiscConfig.heightAboveJukebox, 
                VideoDiscConfig.zOffset
            )
        )
        
        // Create a new display group for this frame
        jukeboxData.displayGroup = RenderGroup()
        
        for (y in 0 until videoHeight) {
            for (x in 0 until videoWidth) {
                val color = frame.sampleColor(
                    x.toFloat() / videoWidth,
                    y.toFloat() / videoHeight
                )
                
                // Skip transparent/black pixels
                if (color.alpha == 0 || (color.red == 0 && color.green == 0 && color.blue == 0)) {
                    continue
                }
                
                val offsetX = (x.toFloat() / videoWidth - 0.5f) * scale
                val offsetY = (y.toFloat() / videoHeight - 0.5f) * scale
                
                // Create transform for forward-facing display
                val transformForward = Matrix4f()
                    .translate(offsetX, offsetY, 0f)
                    .scale(scale / videoWidth, scale / videoHeight, 1f)
                    .mul(textDisplayUnitSquare)
                
                // Create forward-facing display
                jukeboxData.displayGroup["forward_$x" to y] = renderText(
                    world = world,
                    position = videoPosition,
                    init = {
                        it.text = " "
                        it.teleportDuration = VideoDiscConfig.teleportDuration
                        it.interpolationDuration = VideoDiscConfig.interpolationDuration
                        it.brightness = Display.Brightness(VideoDiscConfig.brightness, VideoDiscConfig.brightness)
                        if (VideoDiscConfig.billboard) {
                            it.billboard = Display.Billboard.CENTER
                        }
                    },
                    update = {
                        it.interpolateTransform(transformForward)
                        it.backgroundColor = color
                    }
                )
                
                // Create backward-facing display only if double-sided is enabled
                if (VideoDiscConfig.doubleSided) {
                    // Create transform for backward-facing display (rotated 180 degrees)
                    val transformBackward = Matrix4f()
                        .translate(offsetX, offsetY, 0f)
                        .rotateY(Math.PI.toFloat()) // Rotate 180 degrees around Y axis
                        .scale(scale / videoWidth, scale / videoHeight, 1f)
                        .mul(textDisplayUnitSquare)
                    
                    jukeboxData.displayGroup["backward_$x" to y] = renderText(
                        world = world,
                        position = videoPosition,
                        init = {
                            it.text = " "
                            it.teleportDuration = VideoDiscConfig.teleportDuration
                            it.interpolationDuration = VideoDiscConfig.interpolationDuration
                            it.brightness = Display.Brightness(VideoDiscConfig.brightness, VideoDiscConfig.brightness)
                            if (VideoDiscConfig.billboard) {
                                it.billboard = Display.Billboard.CENTER
                            }
                        },
                        update = {
                            it.interpolateTransform(transformBackward)
                            it.backgroundColor = color
                        }
                    )
                }
            }
        }
        
        jukeboxData.displayGroup.submit("video_${jukeboxLocation.x}_${jukeboxLocation.y}_${jukeboxLocation.z}")
    }
} 