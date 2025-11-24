"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Edit3, CheckCircle, Clock } from "lucide-react"
import { toast } from "@/hooks/use-toast"
import { ProfileService } from "@/lib/profile-service"
import { UserInfo } from "@/lib/auth-client"
import { CountrySelector } from "@/components/ui/country-selector"

interface AddressFormProps {
  user: UserInfo
  onUpdate: () => void
}

interface AddressInfo {
  address: string
  city: string
  country: string
  state: string
  postalCode: string
}

export function AddressForm({ user, onUpdate }: AddressFormProps) {
  const [isEditing, setIsEditing] = useState(false)
  const [isSaving, setIsSaving] = useState(false)

  const [addressInfo, setAddressInfo] = useState<AddressInfo>({
    address: user.address?.street || "",
    city: user.address?.city || "",
    country: user.address?.country || "",
    state: user.address?.state || "",
    postalCode: user.address?.postalCode || "",
  })

  // Update address info when user data changes
  useEffect(() => {
    setAddressInfo({
      address: user.address?.street || "",
      city: user.address?.city || "",
      country: user.address?.country || "",
      state: user.address?.state || "",
      postalCode: user.address?.postalCode || "",
    })
  }, [user.address])

  const handleSaveAddress = async () => {
    setIsSaving(true)
    try {
      const addressAttributes: Record<string, string> = {
        'street': addressInfo.address,
        'city': addressInfo.city,
        'state': addressInfo.state,
        'country': addressInfo.country,
        'postalCode': addressInfo.postalCode,
      }

      const filteredAttributes = Object.fromEntries(
        Object.entries(addressAttributes).filter(([_, value]) => value.trim() !== '')
      )

      if (Object.keys(filteredAttributes).length > 0) {
        await ProfileService.updateAttributes(filteredAttributes)
      }

      await onUpdate()
      setIsEditing(false)
      toast({
        title: "Address Updated",
        description: "Your billing address has been successfully updated.",
      })
    } catch (error) {
      console.error('Failed to update address:', error)
      toast({
        title: "Update Failed",
        description: "Failed to update address. Please try again.",
        variant: "destructive",
      })
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <Card className="bg-white border-gray-200">
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle className="text-gray-900">Địa chỉ thanh toán</CardTitle>
          <CardDescription>Địa chỉ thanh toán và liên hệ của bạn cho các đặt chỗ</CardDescription>
        </div>
        <div className="flex gap-1">
          <Button
            variant={isEditing ? "default" : "outline"}
            onClick={isEditing ? handleSaveAddress : () => setIsEditing(true)}
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
                {isSaving ? "Đang lưu..." : "Lưu địa chỉ"}
              </>
            ) : (
              <>
                <Edit3 className="h-4 w-4 mr-2" />
                Chỉnh sửa địa chỉ
              </>
            )}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-1 md:col-span-2">
            <Label htmlFor="address.street">Địa chỉ đường</Label>
            <Input
              id="address.street"
              value={addressInfo.address}
              onChange={(e) => setAddressInfo((prev) => ({ ...prev, address: e.target.value }))}
              disabled={!isEditing}
              placeholder="Nhập địa chỉ đường của bạn (ví dụ: 123 Đường Chính, Căn hộ 4B)"
              className="bg-white border-gray-300 disabled:opacity-60"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="address.city">Thành phố</Label>
            <Input
              id="address.city"
              value={addressInfo.city}
              onChange={(e) => setAddressInfo((prev) => ({ ...prev, city: e.target.value }))}
              disabled={!isEditing}
              placeholder="Nhập tên thành phố của bạn"
              className="bg-white border-gray-300 disabled:opacity-60"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="address.state">Tiểu bang/Tỉnh</Label>
            <Input
              id="address.state"
              value={addressInfo.state}
              onChange={(e) => setAddressInfo((prev) => ({ ...prev, state: e.target.value }))}
              disabled={!isEditing}
              placeholder="Nhập tiểu bang hoặc tỉnh của bạn (tùy chọn)"
              className="bg-white border-gray-300 disabled:opacity-60"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="address.country">Quốc gia</Label>
            <CountrySelector
              value={addressInfo.country}
              onValueChange={(value) => setAddressInfo((prev) => ({ ...prev, country: value }))}
              disabled={!isEditing}
              placeholder="Chọn quốc gia của bạn"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="address.postalCode">Mã bưu điện</Label>
            <Input
              id="address.postalCode"
              value={addressInfo.postalCode}
              onChange={(e) => setAddressInfo((prev) => ({ ...prev, postalCode: e.target.value }))}
              disabled={!isEditing}
              placeholder="Nhập mã bưu điện/zip của bạn (tùy chọn)"
              className="bg-white border-gray-300 disabled:opacity-60"
            />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
