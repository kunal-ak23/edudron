'use client'

import React, { useEffect, useRef } from 'react'
import videojs from 'video.js'
import 'video.js/dist/video-js.css'

export interface VideoPlayerProps {
  videoUrl: string
  autoplay?: boolean
  className?: string
  onReady?: (player: ReturnType<typeof videojs>) => void
  onDispose?: () => void
  logPrefix?: string
  showAllControls?: boolean
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  videoUrl,
  autoplay = false,
  className = '',
  onReady,
  onDispose,
  logPrefix = 'VideoPlayer',
  showAllControls = true
}) => {
  const videoRef = useRef<HTMLDivElement>(null)
  const playerRef = useRef<ReturnType<typeof videojs> | null>(null)

  useEffect(() => {
    if (!videoRef.current || !videoUrl) return

    // Dispose existing player
    if (playerRef.current) {
      playerRef.current.dispose()
      playerRef.current = null
    }

    // Clear container
    videoRef.current.innerHTML = ''

    // Create Video.js element
    const videoElement = document.createElement('video-js')
    videoElement.className = 'video-js vjs-big-play-centered vjs-fluid'
    videoElement.setAttribute('playsinline', 'true')
    videoElement.setAttribute('crossorigin', 'anonymous')
    videoElement.setAttribute('data-setup', '{}')
    videoRef.current.appendChild(videoElement)

    const player = videojs(videoElement, {
      controls: true,
      autoplay,
      responsive: true,
      fluid: true,
      preload: 'auto',
      playbackRates: [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2],
      html5: {
        vhs: {
          overrideNative: false,
          withCredentials: false
        },
        nativeVideoTracks: false,
        nativeAudioTracks: false,
        nativeTextTracks: false
      },
      sources: [
        {
          src: videoUrl,
          type: 'video/mp4'
        }
      ],
      techOrder: ['html5'],
      controlBar: {
        children: showAllControls
          ? [
              'playToggle',
              'volumePanel',
              'currentTimeDisplay',
              'timeDivider',
              'durationDisplay',
              'progressControl',
              'customControlSpacer',
              'playbackRateMenuButton',
              'chaptersButton',
              'descriptionsButton',
              'subsCapsButton',
              'audioTrackButton',
              'fullscreenToggle',
              'pictureInPictureToggle'
            ]
          : [
              'playToggle',
              'volumePanel',
              'currentTimeDisplay',
              'timeDivider',
              'durationDisplay',
              'progressControl',
              'playbackRateMenuButton',
              'fullscreenToggle'
            ]
      },
      userActions: {
        hotkeys: {
          volumeStep: 0.1,
          seekStep: 5,
          enableModifiersForNumbers: false
        }
      }
    })

    playerRef.current = player

    // Set crossorigin on the HTMLVideoElement for CORS requests
    player.ready(() => {
      const tech = player.tech(true)
      const el = tech?.el() as HTMLVideoElement | undefined
      if (el) {
        el.crossOrigin = 'anonymous'
      }

      if (onReady) {
        onReady(player)
      }
    })


    player.on('error', () => {
      const error = player.error()
      console.error(`[${logPrefix}] Video.js error:`, error)
      if (error) {
        console.error(`[${logPrefix}] Error code:`, error.code)
        console.error(`[${logPrefix}] Error message:`, error.message)
      }
    })

    return () => {
      if (playerRef.current) {
        playerRef.current.dispose()
        playerRef.current = null
        if (onDispose) onDispose()
      }
    }
  }, [videoUrl, autoplay, onReady, onDispose, logPrefix, showAllControls])

  return (
    <div
      ref={videoRef}
      className={className}
      data-vjs-player
    />
  )
}

export default VideoPlayer
