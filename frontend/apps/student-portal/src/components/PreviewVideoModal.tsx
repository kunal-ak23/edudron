'use client'

import React, { useEffect, useRef } from 'react'
import videojs from 'video.js'
import 'video.js/dist/video-js.css'

// Add primary theme styles for Video.js
if (typeof document !== 'undefined') {
  const styleId = 'videojs-primary-theme-modal'
  if (!document.getElementById(styleId)) {
    const style = document.createElement('style')
    style.id = styleId
    style.textContent = `
      /* Primary Theme - Big Play Button */
      .video-js .vjs-big-play-button {
        background-color: hsl(var(--primary-600)) !important;
        border-color: hsl(var(--primary-600)) !important;
        border-radius: 50% !important;
        width: 80px !important;
        height: 80px !important;
        line-height: 80px !important;
        margin-top: -40px !important;
        margin-left: -40px !important;
        border-width: 4px !important;
        transition: all 0.3s ease !important;
        top: 50% !important;
        left: 50% !important;
        transform: translate(-50%, -50%) !important;
      }
      .video-js .vjs-big-play-button:hover {
        background-color: hsl(var(--primary-700)) !important;
        border-color: hsl(var(--primary-700)) !important;
        transform: translate(-50%, -50%) scale(1.1) !important;
      }
      .video-js .vjs-big-play-button .vjs-icon-placeholder:before {
        color: white !important;
        font-size: 2.5em !important;
      }
      
      /* Primary Theme - Control Bar */
      .video-js .vjs-control-bar {
        background: linear-gradient(to top, rgba(0, 0, 0, 0.8), transparent) !important;
        height: 4.5em !important;
        display: flex !important;
        flex-wrap: nowrap !important;
        align-items: center !important;
        justify-content: flex-start !important;
        overflow: visible !important;
        white-space: nowrap !important;
      }
      .video-js .vjs-control-bar > * {
        flex-shrink: 0 !important;
        flex-grow: 0 !important;
        display: inline-flex !important;
      }
      .video-js .vjs-progress-control {
        flex: 1 1 auto !important;
        min-width: 0 !important;
        display: flex !important;
      }
      .video-js .vjs-control {
        display: inline-flex !important;
        align-items: center !important;
        white-space: nowrap !important;
      }
      .video-js .vjs-button {
        display: inline-flex !important;
        align-items: center !important;
        white-space: nowrap !important;
      }
      
      /* Primary Theme - Progress Bar */
      .video-js .vjs-play-progress {
        background-color: hsl(var(--primary-600)) !important;
      }
      .video-js .vjs-load-progress {
        background-color: rgba(255, 255, 255, 0.3) !important;
      }
      .video-js .vjs-progress-holder {
        background-color: rgba(255, 255, 255, 0.2) !important;
      }
      .video-js .vjs-play-progress:before {
        color: hsl(var(--primary-600)) !important;
      }
      
      /* Primary Theme - Control Buttons */
      .video-js .vjs-play-control:hover,
      .video-js .vjs-play-control:focus {
        color: hsl(var(--primary-400)) !important;
      }
      .video-js .vjs-play-control.vjs-playing .vjs-icon-placeholder:before {
        color: hsl(var(--primary-400)) !important;
      }
      .video-js .vjs-play-control.vjs-paused .vjs-icon-placeholder:before {
        color: hsl(var(--primary-400)) !important;
      }
      
      /* Primary Theme - Volume Control */
      .video-js .vjs-volume-level {
        background-color: hsl(var(--primary-600)) !important;
      }
      .video-js .vjs-volume-bar {
        background-color: rgba(255, 255, 255, 0.2) !important;
      }
      .video-js .vjs-volume-control:hover .vjs-volume-level {
        background-color: hsl(var(--primary-700)) !important;
      }
      
      /* Primary Theme - Time Displays */
      .video-js .vjs-current-time,
      .video-js .vjs-duration,
      .video-js .vjs-remaining-time {
        color: rgba(255, 255, 255, 0.9) !important;
        font-weight: 500 !important;
      }
      
      /* Primary Theme - Fullscreen Button */
      .video-js .vjs-fullscreen-control:hover {
        color: hsl(var(--primary-400)) !important;
      }
      
      /* Primary Theme - Picture-in-Picture */
      .video-js .vjs-picture-in-picture-control:hover {
        color: hsl(var(--primary-400)) !important;
      }
      
      /* Primary Theme - Playback Rate Menu */
      .video-js .vjs-playback-rate .vjs-playback-rate-value {
        color: hsl(var(--primary-400)) !important;
        font-weight: 600 !important;
      }
      .video-js .vjs-menu-button-popup .vjs-menu .vjs-menu-content {
        background-color: rgba(0, 0, 0, 0.9) !important;
        border: 1px solid hsl(var(--primary-600)) !important;
      }
      .video-js .vjs-menu li.vjs-menu-item:hover,
      .video-js .vjs-menu li.vjs-menu-item:focus {
        background-color: hsl(var(--primary-600)) !important;
        color: white !important;
      }
      .video-js .vjs-menu li.vjs-menu-item.vjs-selected {
        background-color: hsl(var(--primary-700)) !important;
        color: white !important;
      }
      
      /* Primary Theme - Subtitles/Captions Button */
      .video-js .vjs-subs-caps-button:hover,
      .video-js .vjs-chapters-button:hover,
      .video-js .vjs-descriptions-button:hover {
        color: hsl(var(--primary-400)) !important;
      }
      
      /* Primary Theme - Seek Bar Handle */
      .video-js .vjs-progress-control .vjs-play-progress .vjs-time-tooltip {
        background-color: hsl(var(--primary-600)) !important;
        color: white !important;
        border: 1px solid hsl(var(--primary-700)) !important;
      }
      
      /* Primary Theme - Loading Spinner */
      .video-js .vjs-loading-spinner {
        border-color: hsl(var(--primary-600)) transparent transparent transparent !important;
      }
      
      /* Video Centering */
      .video-js {
        display: flex !important;
        align-items: center !important;
        justify-content: center !important;
      }
      .video-js .vjs-tech {
        object-position: center center !important;
      }
      .video-js video {
        object-position: center center !important;
      }
      .video-js .vjs-poster {
        object-position: center center !important;
      }
    `
    document.head.appendChild(style)
  }
}

interface PreviewVideoModalProps {
  videoUrl: string
  courseTitle: string
  isOpen: boolean
  onClose: () => void
}

export function PreviewVideoModal({ videoUrl, courseTitle, isOpen, onClose }: PreviewVideoModalProps) {
  const videoRef = useRef<HTMLDivElement>(null)
  const playerRef = useRef<any>(null)

  useEffect(() => {
    if (isOpen && videoRef.current && !playerRef.current) {
      // Clear and prepare container
      videoRef.current.innerHTML = ''
      
      // Create video element for Video.js
      const videoElement = document.createElement('video-js')
      videoElement.className = 'video-js vjs-big-play-centered vjs-fluid'
      videoElement.setAttribute('playsinline', 'true')
      videoElement.setAttribute('data-setup', '{}')
      videoRef.current.appendChild(videoElement)
      
      // Initialize Video.js player with enhanced controls
      const player = videojs(videoElement, {
        controls: true,
        autoplay: true,
        responsive: true,
        fluid: true, // Use fluid mode for responsive sizing
        playbackRates: [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2],
        preload: 'auto',
        // Enable seeking to unbuffered positions
        html5: {
          vhs: {
            overrideNative: true
          },
          nativeVideoTracks: false,
          nativeAudioTracks: false,
          nativeTextTracks: false
        },
        sources: [{
          src: videoUrl,
          type: 'video/mp4'
        }],
        // Control bar configuration with all useful controls
        controlBar: {
          children: [
            'playToggle',
            'volumePanel',
            'currentTimeDisplay',
            'timeDivider',
            'durationDisplay',
            'progressControl',
            'remainingTimeDisplay',
            'customControlSpacer',
            'playbackRateMenuButton', // Playback speed control (0.5x to 2x)
            'chaptersButton',
            'descriptionsButton',
            'subsCapsButton',
            'audioTrackButton',
            'fullscreenToggle',
            'pictureInPictureToggle'
          ]
        },
        // Enable keyboard shortcuts
        userActions: {
          hotkeys: {
            volumeStep: 0.1,
            seekStep: 5,
            enableModifiersForNumbers: false
          }
        }
      })
      
      // Ensure proper seeking behavior
      player.ready(() => {
        const tech = player.tech()
        if (tech && tech.el_) {
          const videoEl = tech.el_ as HTMLVideoElement
          
          // Enable seeking to unbuffered positions
          videoEl.setAttribute('preload', 'auto')
          
          // Get the progress control and ensure accurate seeking
          const progressControl = player.getChild('ControlBar')?.getChild('ProgressControl')
          if (progressControl) {
            const seekBar = progressControl.getChild('SeekBar')
            if (seekBar) {
              // Override the seek behavior to ensure accurate seeking
              seekBar.on('click', (event: any) => {
                const seekBarEl = seekBar.el()
                if (seekBarEl) {
                  const rect = seekBarEl.getBoundingClientRect()
                  const percent = (event.clientX - rect.left) / rect.width
                  const duration = player.duration()
                  if (duration && !isNaN(duration)) {
                    const newTime = percent * duration
                    // Seek to the exact clicked position
                    player.currentTime(newTime)
                    videoEl.currentTime = newTime
                  }
                }
              })
            }
          }
          
          // Handle seeking to ensure it goes to the requested time
          player.on('seeking', () => {
            const requestedTime = player.currentTime()
            // Ensure the underlying video element seeks to the same time
            if (requestedTime !== undefined && !isNaN(requestedTime)) {
              const time = requestedTime as number
              if (videoEl.currentTime !== time) {
                videoEl.currentTime = time
              }
            }
          })
          
          // Improve buffering for better seeking experience
          player.on('canplay', () => {
            // Video is ready, ensure seeking works properly
            if (videoEl.readyState >= 2) {
              // Video has enough data to seek
            }
          })
          
          // Handle video loadedmetadata to preserve aspect ratio
          player.on('loadedmetadata', () => {
            const videoWidth = videoEl.videoWidth
            const videoHeight = videoEl.videoHeight
            if (videoWidth && videoHeight && videoWidth > 0 && videoHeight > 0) {
              // Calculate aspect ratio percentage for padding-top (fluid mode)
              const aspectRatioPercent = (videoHeight / videoWidth) * 100
              // Set the video element to maintain its natural aspect ratio
              const playerEl = player.el()
              if (playerEl) {
                // Update padding-top to match video's aspect ratio
                playerEl.style.paddingTop = `${aspectRatioPercent}%`
                // Ensure the video tech maintains aspect ratio
                if (tech.el_) {
                  tech.el_.style.objectFit = 'contain'
                }
              }
            }
          })
        }
      })
      
      playerRef.current = player
    }
    
    // Cleanup on unmount or when modal closes
    return () => {
      if (playerRef.current) {
        playerRef.current.dispose()
        playerRef.current = null
      }
    }
  }, [isOpen, videoUrl])

  if (!isOpen) return null

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-75 backdrop-blur-sm"
      onClick={onClose}
    >
      <div 
        className="bg-black rounded-2xl shadow-2xl max-w-5xl w-full mx-4 relative"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute -top-10 right-0 text-white hover:text-gray-300 font-semibold text-xl leading-none z-10"
          aria-label="Close"
        >
          ✕
        </button>

        {/* Video Player */}
        <div className="relative w-full" style={{ paddingBottom: '56.25%' }}> {/* 16:9 aspect ratio */}
          <div data-vjs-player ref={videoRef} className="absolute top-0 left-0 w-full h-full rounded-t-2xl" />
        </div>

        {/* Course Title */}
        <div className="px-6 py-4 bg-gray-900 rounded-b-2xl">
          <h3 className="text-white font-semibold text-lg">{courseTitle}</h3>
        </div>
      </div>
    </div>
  )
}

