import type { Metadata } from 'next'
import { Inter, Manrope } from 'next/font/google'
import '@kunal-ak23/edudron-ui-components/styles.css'
import './globals.css'
import { Providers } from './providers'
import { ConditionalLayout } from '@/components/ConditionalLayout'
import { Toaster } from '@/components/ui/toaster'

const inter = Inter({ subsets: ['latin'] })
const manrope = Manrope({ 
  subsets: ['latin'],
  weight: ['600'],
  variable: '--font-manrope'
})

export const metadata: Metadata = {
  title: 'EduDron Admin Dashboard',
  description: 'Learning Management System Admin Dashboard',
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
      <body className={`${inter.className} ${manrope.variable}`}>
        <Providers>
          <ConditionalLayout>
            {children}
          </ConditionalLayout>
        </Providers>
        <Toaster />
      </body>
    </html>
  )
}

