/** @type {import('next').NextConfig} */

const nextConfig = {
  transpilePackages: ['@edudron/ui-components', '@edudron/shared-utils'],
  // Note: 'standalone' output removed to fix build - can be re-enabled if needed
  // output: 'standalone',
  trailingSlash: false,
  // Disable static optimization for pages that use client-side features
  experimental: {
    missingSuspenseWithCSRBailout: false,
  },
}

module.exports = nextConfig

