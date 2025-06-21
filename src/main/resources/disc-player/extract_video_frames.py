#!/usr/bin/env python3
"""
Video Frame Extractor for Minecraft Hologram Plugin

This script extracts frames from a video file and saves them as images
that can be used by the video player in Minecraft.

Usage:
    python extract_video_frames.py [video_file] [output_directory] [frame_rate]

Example:
    python extract_video_frames.py my_video.mp4 frames 30
"""

import cv2
import os
import sys
import argparse
from pathlib import Path

def extract_frames(video_path, output_dir, frame_rate=30, max_frames=None):
    """
    Extract frames from a video file.
    
    Args:
        video_path (str): Path to the input video file
        output_dir (str): Directory to save extracted frames
        frame_rate (int): Target frame rate (frames per second)
        max_frames (int): Maximum number of frames to extract (None for all)
    """
    
    # Open the video file
    cap = cv2.VideoCapture(video_path)
    
    if not cap.isOpened():
        print(f"Error: Could not open video file {video_path}")
        return False
    
    # Get video properties
    original_fps = cap.get(cv2.CAP_PROP_FPS)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration = total_frames / original_fps
    
    print(f"Video properties:")
    print(f"  Original FPS: {original_fps}")
    print(f"  Total frames: {total_frames}")
    print(f"  Duration: {duration:.2f} seconds")
    print(f"  Target FPS: {frame_rate}")
    
    # Calculate frame interval
    frame_interval = original_fps / frame_rate
    print(f"  Frame interval: {frame_interval:.2f} (extract every {frame_interval:.1f} frames)")
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    # Extract frames
    frame_count = 0
    extracted_count = 0
    target_frame = 0
    
    print(f"\nExtracting frames to {output_path}...")
    
    while True:
        ret, frame = cap.read()
        
        if not ret:
            break
        
        # Check if this frame should be extracted
        if frame_count >= target_frame:
            # Save frame
            frame_filename = f"frame_{extracted_count:06d}.png"
            frame_path = output_path / frame_filename
            
            # Resize frame to Minecraft-friendly dimensions (64x64)
            resized_frame = cv2.resize(frame, (64, 64))
            
            # Save as PNG
            cv2.imwrite(str(frame_path), resized_frame)
            
            extracted_count += 1
            target_frame = frame_count + frame_interval
            
            # Progress indicator
            if extracted_count % 100 == 0:
                print(f"  Extracted {extracted_count} frames...")
            
            # Check if we've reached the maximum number of frames
            if max_frames and extracted_count >= max_frames:
                break
        
        frame_count += 1
    
    cap.release()
    
    print(f"\nExtraction complete!")
    print(f"  Extracted {extracted_count} frames")
    print(f"  Output directory: {output_path}")
    print(f"  Frame format: PNG (64x64 pixels)")
    
    return True

def main():
    parser = argparse.ArgumentParser(
        description="Extract frames from a video file for Minecraft video player"
    )
    parser.add_argument(
        "video_file",
        help="Path to the input video file"
    )
    parser.add_argument(
        "output_directory",
        help="Directory to save extracted frames"
    )
    parser.add_argument(
        "--frame-rate", "-f",
        type=int,
        default=30,
        help="Target frame rate (default: 30)"
    )
    parser.add_argument(
        "--max-frames", "-m",
        type=int,
        help="Maximum number of frames to extract"
    )
    
    args = parser.parse_args()
    
    # Check if video file exists
    if not os.path.exists(args.video_file):
        print(f"Error: Video file '{args.video_file}' not found")
        sys.exit(1)
    
    # Extract frames
    success = extract_frames(
        args.video_file,
        args.output_directory,
        args.frame_rate,
        args.max_frames
    )
    
    if not success:
        sys.exit(1)
    
    print("\nNext steps:")
    print("1. Copy the extracted frames to your Minecraft server's plugin data folder")
    print("2. Configure the music_disc_config.yml file")
    print("3. Restart your Minecraft server")
    print("4. Use the custom music disc in a jukebox!")

if __name__ == "__main__":
    main() 