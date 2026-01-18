/** @type {import('next').NextConfig} */

const nextConfig = {
  transpilePackages: [
    '@edudron/ui-components',
    '@edudron/shared-utils',
    '@kunal-ak23/edudron-ui-components',
    '@kunal-ak23/edudron-shared-utils',
    'video.js',
  ],
  output: 'standalone',
  trailingSlash: false,
  // Disable static optimization for pages that use client-side features
  experimental: {
    missingSuspenseWithCSRBailout: false,
  },
}

module.exports = nextConfig

