import type React from "react";
import type { Metadata } from "next";
import { GeistSans } from "geist/font/sans";
import { GeistMono } from "geist/font/mono";
import "@/styles/globals.css";
import { AuthProvider } from "@/contexts/auth-context";
import { PreferencesProvider } from "@/contexts/preferences-context";
import { BookingProvider } from "@/contexts/booking-context";
import { ThemeProvider } from "@/contexts/theme-context";
import { Toaster } from "@/components/ui/toaster";
import { RecommendPanelProvider } from "@/contexts/recommend-panel-context";
import { AppShell } from "@/components/app-shell";

export const metadata: Metadata = {
  title: "BookingSmart - Smart Travel Planning",
  description: "AI-powered travel planning platform for flights and hotels",
  icons: {
    icon: "/favicon.ico",
    shortcut: "/favicon-32x32.png",
    apple: "/apple-touch-icon.png",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full overflow-hidden">
      <body
        className={`font-sans ${GeistSans.variable} ${GeistMono.variable} antialiased h-full bg-background text-foreground overflow-hidden`}
      >
        <ThemeProvider>
          <AuthProvider>
            <PreferencesProvider>
              <BookingProvider>
                <RecommendPanelProvider>
                  <AppShell>{children}</AppShell>
                </RecommendPanelProvider>
              </BookingProvider>
            </PreferencesProvider>
          </AuthProvider>
        </ThemeProvider>
        <Toaster />
      </body>
    </html>
  );
}
