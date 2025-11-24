import type React from "react"
import type { Metadata } from "next"
import { Inter } from "next/font/google"
import "./globals.css"
import "react-day-picker/style.css"

const inter = Inter({ subsets: ["latin"] })

export const metadata: Metadata = {
  title: "BookingSmart Admin",

}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="vi">
      <body className={inter.className}>{children}</body>
    </html>
  )
}
