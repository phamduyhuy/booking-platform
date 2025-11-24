import { z } from 'zod';

// User Preferences Schema
export const UserPreferencesSchema = z.object({
  language: z.string().regex(/^[a-z]{2}(-[A-Z]{2})?$/, 'Must be a valid language code (e.g., "en", "en-US")').optional(),
  currency: z.string().regex(/^[A-Z]{3}$/, 'Must be a valid 3-letter currency code').optional(),
  timezone: z.string().optional(), // User's preferred timezone
  dateFormat: z.enum(['VN', 'US', 'ISO']).optional(), // Date format preference
  theme: z.enum(['light', 'dark', 'auto']).optional(),
  density: z.enum(['compact', 'comfortable', 'spacious']).optional(),
  notifications: z.enum(['email', 'sms', 'push', 'all', 'none']).optional(),
  autoDetectLocation: z.boolean().optional(), // Auto-detect location on login
  countryCode: z.string().optional(), // User's country code
});

// User Address Schema
export const UserAddressSchema = z.object({
  street: z.string().min(3, 'Street address must be at least 3 characters').max(200, 'Street address must be no more than 200 characters').optional(),
  city: z.string().min(2, 'City must be at least 2 characters').max(50, 'City must be no more than 50 characters').optional(),
  state: z.string().min(2, 'State must be at least 2 characters').max(50, 'State must be no more than 50 characters').optional(),
  country: z.string().min(2, 'Country must be at least 2 characters').max(50, 'Country must be no more than 50 characters').optional(),
  postalCode: z.string().min(3, 'Postal code must be at least 3 characters').max(20, 'Postal code must be no more than 20 characters').optional(),
});

// User Info Schema
export const UserInfoSchema = z.object({
  id: z.string(),
  username: z.string(),
  email: z.string().email('Must be a valid email address'),
  firstName: z.string().max(255, 'First name must be no more than 255 characters'),
  lastName: z.string().max(255, 'Last name must be no more than 255 characters'),
  fullName: z.string(),
  picture: z.string().url('Must be a valid URL').optional(),
  roles: z.array(z.string()),
  permissions: z.array(z.string()),
  partnerType: z.enum(['HOTEL', 'FLIGHT', 'TRANSPORT', 'ALL']).optional(),
  partnerServices: z.array(z.string()).optional(),
  preferences: UserPreferencesSchema.optional(),
  address: UserAddressSchema.optional(),
  phone: z.string().regex(/^\+?[1-9]\d{1,14}$/, 'Must be a valid phone number with country code').optional(),
  dateOfBirth: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Must be in YYYY-MM-DD format').optional(),
  gender: z.enum(['male', 'female', 'other']).optional(),
});

// Attribute Update Request Schema
export const AttributeUpdateRequestSchema = z.object({
  attributes: z.record(z.string(), z.union([z.string(), z.array(z.string())])),
});

// Single Attribute Update Request Schema
export const SingleAttributeUpdateRequestSchema = z.object({
  value: z.union([z.string(), z.array(z.string())]),
});

// Profile Update Request Schema
export const ProfileUpdateRequestSchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(255, 'First name must be no more than 255 characters'),
  lastName: z.string().min(1, 'Last name is required').max(255, 'Last name must be no more than 255 characters'),
  email: z.string().email('Must be a valid email address'),
});

// Password Update Request Schema
export const PasswordUpdateRequestSchema = z.object({
  currentPassword: z.string().min(1, 'Current password is required'),
  newPassword: z.string().min(8, 'New password must be at least 8 characters long'),
});

// Picture Update Request Schema
export const PictureUpdateRequestSchema = z.object({
  pictureUrl: z.string().url('Must be a valid URL'),
});

// Export types
export type UserPreferences = z.infer<typeof UserPreferencesSchema>;
export type UserAddress = z.infer<typeof UserAddressSchema>;
export type UserInfo = z.infer<typeof UserInfoSchema>;
export type AttributeUpdateRequest = z.infer<typeof AttributeUpdateRequestSchema>;
export type SingleAttributeUpdateRequest = z.infer<typeof SingleAttributeUpdateRequestSchema>;
export type ProfileUpdateRequest = z.infer<typeof ProfileUpdateRequestSchema>;
export type PasswordUpdateRequest = z.infer<typeof PasswordUpdateRequestSchema>;
export type PictureUpdateRequest = z.infer<typeof PictureUpdateRequestSchema>;
