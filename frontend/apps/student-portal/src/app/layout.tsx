import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import '@kunal-ak23/edudron-ui-components/styles.css'
import './globals.css'
import { Providers } from './providers'
import { DynamicHead } from '@/components/DynamicHead'

const inter = Inter({ subsets: ['latin'] })

export const metadata: Metadata = {
  title: 'EduDron',
  description: 'Learning Management System Student Portal',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              (function() {
                try {
                  const stored = localStorage.getItem('edudron_font_size');
                  if (stored !== null) {
                    const fontSize = parseFloat(stored);
                    if (!isNaN(fontSize) && fontSize >= 0.5 && fontSize <= 1.5) {
                      document.documentElement.style.setProperty('--app-scale', fontSize.toString());
                    }
                  }
                } catch (e) {
                  // Ignore errors
                }
              })();
            `,
          }}
        />
      </head>
      <body className={inter.className}>
        <Providers>
          <DynamicHead />
          {children}
        </Providers>
      </body>
    </html>
  )
}


