/**
 * Hook for formatting dates with user's timezone and locale preferences
 */

import { usePreferences } from '@/contexts/preferences-context'
import { format, formatRelative as formatRelativeOriginal, parseISO } from 'date-fns'
import { vi } from 'date-fns/locale'
import { formatInTimeZone, toZonedTime } from 'date-fns-tz'

/**
 * Parse a date string as UTC, handling various formats
 */
function parseUtcDate(dateString: string): Date {
  // If it already ends with Z or has timezone info, parse normally
  if (dateString.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(dateString)) {
    return parseISO(dateString)
  }
  
  // If it's an ISO-like string without timezone, treat as UTC
  if (dateString.includes('T')) {
    return parseISO(`${dateString}Z`)
  }
  
  // For other formats, parse normally
  return parseISO(dateString)
}

/**
 * Parse a date input (string or Date) as UTC
 */
function parseUtcDateInput(dateInput: string | Date): Date {
  if (typeof dateInput === 'string') {
    return parseUtcDate(dateInput)
  }
  return dateInput
}

export function useDateFormatter() {
  const { getTimezone, getLanguage } = usePreferences()
  const language = getLanguage() || 'vi'
  const timezone = getTimezone()
  
  // Use Vietnamese locale by default, or fallback to vi if not available
  const locale = language.startsWith('vi') || language === 'vn' ? vi : vi

  /**
   * Format UTC date to user's local time using dd/MM/yyyy HH:MM:SS format
   */
  const formatDateTime = (dateInput: string | Date): string => {
    try {
      const date = parseUtcDateInput(dateInput)
      return formatInTimeZone(date, timezone, 'dd/MM/yyyy HH:mm:ss', { locale })
    } catch {
      return typeof dateInput === 'string' ? dateInput : ''
    }
  }

  /**
   * Format date only (dd/MM/yyyy)
   */
  const formatDateOnly = (dateInput: string | Date): string => {
    try {
      const date = parseUtcDateInput(dateInput)
      return formatInTimeZone(date, timezone, 'dd/MM/yyyy', { locale })
    } catch {
      return typeof dateInput === 'string' ? dateInput : ''
    }
  }

  /**
   * Format time only (HH:mm)
   */
  const formatTimeOnly = (dateInput: string | Date): string => {
    try {
      const date = parseUtcDateInput(dateInput)
      return formatInTimeZone(date, timezone, 'HH:mm', { locale })
    } catch {
      return typeof dateInput === 'string' ? dateInput : ''
    }
  }

  /**
   * Get relative time using formatRelative from date-fns with locale and timezone
   */
  const formatRelative = (dateInput: string | Date): string => {
    try {
      const date = parseUtcDateInput(dateInput)
      // Convert UTC time to user's timezone before formatting relative time
      const zonedDate = toZonedTime(date, timezone)
      const now = new Date()
      return formatRelativeOriginal(zonedDate, now, { locale })
    } catch {
      return typeof dateInput === 'string' ? dateInput : ''
    }
  }

  return {
    formatDateTime,
    formatDateOnly,
    formatTimeOnly,
    formatRelative,
    timezone,
    language,
  }
}
