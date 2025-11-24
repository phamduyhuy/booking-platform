"use client"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Edit3, CheckCircle, Clock } from "lucide-react"
import { toast } from "@/hooks/use-toast"
import { ProfileService } from "@/lib/profile-service"
import { mediaService } from "@/modules/media"
import { UserInfo } from "@/lib/auth-client"

type PhoneCountry = {
  code: string
  dialCode: string
  label: string
}

const PHONE_COUNTRIES: PhoneCountry[] = [
  { code: "VN", dialCode: "+84", label: "Việt Nam" },
  { code: "US", dialCode: "+1", label: "Hoa Kỳ" },
  { code: "SG", dialCode: "+65", label: "Singapore" },
  { code: "JP", dialCode: "+81", label: "Nhật Bản" },
  { code: "KR", dialCode: "+82", label: "Hàn Quốc" },
  { code: "AU", dialCode: "+61", label: "Úc" },
]

const DEFAULT_PHONE_COUNTRY = PHONE_COUNTRIES[0]

const parsePhoneNumber = (rawPhone: string): { country: PhoneCountry; number: string } => {
  if (!rawPhone) {
    return { country: DEFAULT_PHONE_COUNTRY, number: "" }
  }

  const trimmed = rawPhone.trim()
  if (!trimmed) {
    return { country: DEFAULT_PHONE_COUNTRY, number: "" }
  }

  const normalized = trimmed.startsWith("+")
    ? trimmed.replace(/\s+/g, "")
    : `+${trimmed.replace(/\s+/g, "")}`

  const matchedCountry =
    PHONE_COUNTRIES.find((country) => normalized.startsWith(country.dialCode)) ?? DEFAULT_PHONE_COUNTRY

  const nationalNumber = normalized
    .slice(matchedCountry.dialCode.length)
    .replace(/^0+/, "") // remove leading zeros to avoid duplicates with country code

  return {
    country: matchedCountry,
    number: nationalNumber,
  }
}

const normalizeNationalNumber = (number: string): string => number.replace(/\s+/g, "").replace(/^0+/, "")

const buildE164PhoneNumber = (dialCode: string, rawNumber: string): string => {
  const sanitizedDial = dialCode.replace(/\D+/g, "")
  const nationalNumber = normalizeNationalNumber(rawNumber).replace(/\D+/g, "")
  if (!nationalNumber) {
    return ""
  }
  return `+${sanitizedDial}${nationalNumber}`
}

const formatInternationalPhone = (dialCode: string, number: string): string => {
  const nationalNumber = normalizeNationalNumber(number).replace(/\D+/g, "")
  if (!nationalNumber) {
    return ""
  }
  return `${dialCode} ${nationalNumber}`.trim()
}

interface ProfileInfoProps {
  user: UserInfo
  onUpdate: () => void
}

interface UserProfileInfo {
  fullName: string
  username: string
  email: string
  phone: string
  dateOfBirth: string
  gender: string
}

export function ProfileInfo({ user, onUpdate }: ProfileInfoProps) {
  const [isEditing, setIsEditing] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false)
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null)

  const [userInfo, setUserInfo] = useState<UserProfileInfo>({
    fullName: user.fullName || "",
    username: user.username || "",
    email: user.email || "",
    phone: user.phone || "",
    dateOfBirth: user.dateOfBirth || "",
    gender: user.gender || "",
  })

  const initialPhone = parsePhoneNumber(user.phone || "")
  const [phoneCountry, setPhoneCountry] = useState<PhoneCountry>(initialPhone.country)
  const [phoneNumber, setPhoneNumber] = useState<string>(initialPhone.number)

  const handleSaveProfile = async () => {
    setIsSaving(true)
    try {
      const profileData = {
        firstName: userInfo.fullName.split(' ')[0] || '',
        lastName: userInfo.fullName.split(' ').slice(1).join(' ') || '',
        email: userInfo.email,
      }

      const formattedPhone = buildE164PhoneNumber(phoneCountry.dialCode, phoneNumber)
      const phoneDisplayLabel = formatInternationalPhone(phoneCountry.dialCode, phoneNumber)

      await ProfileService.updateProfile(profileData)
      
      // Update basic info attributes
      const basicAttributes: Record<string, string> = {
        'phone': formattedPhone,
        'dateOfBirth': userInfo.dateOfBirth,
        'gender': userInfo.gender,
      }

      const filteredAttributes = Object.fromEntries(
        Object.entries(basicAttributes).filter(([_, value]) => value.trim() !== '')
      )

      if (Object.keys(filteredAttributes).length > 0) {
        await ProfileService.updateAttributes(filteredAttributes)
      }

      await onUpdate()
      setUserInfo((prev) => ({
        ...prev,
        phone: formattedPhone,
      }))
      setPhoneNumber(normalizeNationalNumber(phoneNumber))
      setIsEditing(false)
      toast({
        title: "Hồ sơ đã được cập nhật",
        description: formattedPhone
          ? `Số điện thoại đã được lưu là ${phoneDisplayLabel}`
          : "Hồ sơ của bạn đã được cập nhật thành công.",
      })
    } catch (error) {
      console.error('Failed to update profile:', error)
      toast({
        title: "Cập nhật thất bại",
        description: "Không thể cập nhật hồ sơ. Vui lòng thử lại.",
        variant: "destructive",
      })
    } finally {
      setIsSaving(false)
    }
  }

  const handleAvatarUpload = async (file: File) => {
    if (!file) return

    if (!file.type.startsWith('image/')) {
      toast({
        title: "Loại tệp không hợp lệ",
        description: "Vui lòng chọn một tệp hình ảnh.",
        variant: "destructive",
      })
      return
    }

    if (file.size > 5 * 1024 * 1024) {
      toast({
        title: "Tệp quá lớn",
        description: "Vui lòng chọn một hình ảnh nhỏ hơn 5MB.",
        variant: "destructive",
      })
      return
    }

    setIsUploadingAvatar(true)
    try {
      const uploadResult = await mediaService.uploadImage(file, 'avatars')
      await ProfileService.updatePicture({ pictureUrl: uploadResult.secureUrl })
      await onUpdate()
      setAvatarPreview(uploadResult.secureUrl)
      toast({
        title: "Ảnh đại diện đã được cập nhật",
        description: "Ảnh đại diện của bạn đã được cập nhật thành công.",
      })
    } catch (error) {
      console.error('Failed to update avatar:', error)
      toast({
        title: "Cập nhật ảnh đại diện thất bại",
        description: "Không thể cập nhật ảnh đại diện. Vui lòng thử lại.",
        variant: "destructive",
      })
    } finally {
      setIsUploadingAvatar(false)
    }
  }

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) {
      handleAvatarUpload(file)
    }
  }

  return (
    <Card className="border-gray-200 bg-white">
      <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <CardTitle className="text-gray-900">Thông tin cá nhân</CardTitle>
          <CardDescription>Cập nhật chi tiết cá nhân và thông tin liên hệ của bạn</CardDescription>
        </div>
        <div className="flex flex-wrap gap-1 sm:flex-nowrap">
          <Button
            variant={isEditing ? "default" : "outline"}
            onClick={isEditing ? handleSaveProfile : () => setIsEditing(true)}
            disabled={isSaving}
            className={isEditing ? "bg-cyan-500 hover:bg-cyan-600" : "border-gray-300 hover:bg-gray-50"}
          >
            {isEditing ? (
              <>
                {isSaving ? (
                  <Clock className="h-4 w-4 mr-2 animate-spin" />
                ) : (
                  <CheckCircle className="h-4 w-4 mr-2" />
                )}
                {isSaving ? "Đang lưu..." : "Lưu thay đổi"}
              </>
            ) : (
              <>
                <Edit3 className="h-4 w-4 mr-2" />
                Chỉnh sửa hồ sơ
              </>
            )}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Avatar Section */}
        <div className="flex flex-col items-start gap-3 sm:flex-row sm:items-center sm:gap-4">
          <div className="relative group">
            <Avatar className="h-20 w-20">
              <AvatarImage
                src={user.picture || avatarPreview || "/placeholder.svg?height=80&width=80"}
              />
              <AvatarFallback className="bg-cyan-500/10 text-cyan-400 text-2xl">
                {userInfo.fullName
                  .split(" ")
                  .map((n) => n[0])
                  .join("")}
              </AvatarFallback>
            </Avatar>
            <div className="absolute inset-0 bg-black/50 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer">
              {isUploadingAvatar ? (
                <Clock className="h-6 w-6 text-white animate-spin" />
              ) : (
                <Edit3 className="h-6 w-6 text-white" />
              )}
            </div>
            <input
              type="file"
              accept="image/*"
              onChange={handleFileSelect}
              disabled={isUploadingAvatar}
              className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed"
            />
          </div>
          <div>
            <h3 className="text-lg font-medium text-gray-900">{userInfo.fullName}</h3>
            <p className="text-gray-600">{userInfo.email}</p>
            <p className="text-sm text-gray-500">Nhấp để thay đổi ảnh đại diện</p>
          </div>
        </div>

        {/* Form Fields */}
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="name">Họ và tên</Label>
            <Input
              id="name"
              value={userInfo.fullName}
              onChange={(e) => setUserInfo((prev) => ({ ...prev, fullName: e.target.value }))}
              disabled={!isEditing}
              className="bg-white border-gray-300 disabled:opacity-60"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="username">Tên người dùng</Label>
            <Input
              id="username"
              value={userInfo.username}
              disabled={true}
              className="bg-white border-gray-300 disabled:opacity-60"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="email">Địa chỉ email</Label>
            <Input
              id="email"
              type="email"
              value={userInfo.email}
              onChange={(e) => setUserInfo((prev) => ({ ...prev, email: e.target.value }))}
              disabled={!isEditing}
              className="bg-white border-gray-300 disabled:opacity-60"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="phone">Số điện thoại</Label>
            <div className="flex flex-col gap-1 sm:flex-row">
              <Select
                value={phoneCountry.code}
                onValueChange={(code) => {
                  const selected = PHONE_COUNTRIES.find((country) => country.code === code) ?? DEFAULT_PHONE_COUNTRY
                  setPhoneCountry(selected)
                  setPhoneNumber((prev) => prev.replace(/^0+/, ""))
                }}
                disabled={!isEditing}
              >
                <SelectTrigger className="w-full bg-white border-gray-300 disabled:opacity-60 sm:w-[140px]">
                  <SelectValue placeholder="Quốc gia" />
                </SelectTrigger>
                <SelectContent>
                  {PHONE_COUNTRIES.map((country) => (
                    <SelectItem key={country.code} value={country.code}>
                      {country.label} ({country.dialCode})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Input
                id="phone"
                type="tel"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value.replace(/[^\d\s]/g, ""))}
                disabled={!isEditing}
                placeholder="772 726 533"
                className="bg-white border-gray-300 disabled:opacity-60 sm:flex-1"
              />
            </div>
            <p className="text-xs text-gray-500">
              {formatInternationalPhone(phoneCountry.dialCode, phoneNumber) || `${phoneCountry.dialCode} • Ví dụ: 772 726 533`}
            </p>
          </div>
          <div className="space-y-1">
            <Label htmlFor="dateOfBirth">Ngày sinh</Label>
            <Input
              id="dateOfBirth"
              type="date"
              value={userInfo.dateOfBirth}
              onChange={(e) => setUserInfo((prev) => ({ ...prev, dateOfBirth: e.target.value }))}
              disabled={!isEditing}
              className="bg-white border-gray-300 disabled:opacity-60"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="gender">Giới tính</Label>
            <Select
              value={userInfo.gender}
              onValueChange={(value) => setUserInfo((prev) => ({ ...prev, gender: value }))}
              disabled={!isEditing}
            >
              <SelectTrigger className="w-full bg-white border-gray-300 disabled:opacity-60">
                <SelectValue placeholder="Chọn giới tính" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="male">Nam</SelectItem>
                <SelectItem value="female">Nữ</SelectItem>
                <SelectItem value="other">Khác</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
