"use client"

import React, { useState, useEffect, useRef } from 'react'
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { 
  Select, 
  SelectContent, 
  SelectItem, 
  SelectTrigger, 
  SelectValue 
} from "@/components/ui/select"
import { Calendar } from "@/components/ui/calendar"
import { 
  Popover, 
  PopoverContent, 
  PopoverTrigger 
} from "@/components/ui/popover"
import { useDateFormatter } from "@/hooks/use-date-formatter"
import { useToast } from "@/hooks/use-toast"
import { CalendarIcon } from "lucide-react"
import { cn } from "@/lib/utils"
import { HotelBookingDetails, GuestDetails } from '@/modules/booking/types'
import { SelectedHotel } from '@/types'

const parseDate = (dateString?: string): Date | undefined => {
  if (!dateString) return undefined;
  const date = new Date(dateString);
  // Check if the date is valid. Invalid dates can result from parsing "undefined" or "null"
  if (isNaN(date.getTime())) {
    return undefined;
  }
  return date;
};

const MAX_GUESTS = 10

const buildGuest = (type: GuestDetails['guestType'], existing?: GuestDetails): GuestDetails => ({
  guestType: type,
  title: existing?.title ?? '',
  firstName: existing?.firstName ?? '',
  lastName: existing?.lastName ?? '',
  dateOfBirth: existing?.dateOfBirth ?? '',
  gender: existing?.gender === 'F' ? 'F' : 'M',
  nationality: existing?.nationality ?? 'VN',
  idNumber: existing?.idNumber,
  email: type === 'PRIMARY' ? existing?.email ?? '' : existing?.email,
  phoneNumber: type === 'PRIMARY' ? existing?.phoneNumber ?? '' : existing?.phoneNumber,
  loyaltyNumber: existing?.loyaltyNumber,
  specialRequests: existing?.specialRequests,
})

const buildGuestList = (count: number, existing?: GuestDetails[]): GuestDetails[] => {
  const desired = Math.max(1, Math.min(MAX_GUESTS, count))
  const source = existing ?? []
  const primary = buildGuest('PRIMARY', source[0])
  const additionals: GuestDetails[] = []
  for (let i = 0; i < desired - 1; i += 1) {
    additionals.push(buildGuest('ADDITIONAL', source[i + 1]))
  }
  return [primary, ...additionals]
}

interface HotelBookingFormProps {
  hotel: SelectedHotel
  onSubmit: (details: HotelBookingDetails) => void
  onCancel: () => void
}

export function HotelBookingForm({ hotel, onSubmit, onCancel }: HotelBookingFormProps) {
  if (!hotel) {
    return (
      <div className="rounded-lg border border-dashed border-muted-foreground/40 bg-muted/10 p-6 text-center text-sm text-muted-foreground">
        Không có thông tin khách sạn. Vui lòng quay lại trang trước để chọn khách sạn.
      </div>
    )
  }
  
  const { formatDateOnly } = useDateFormatter()
  const { toast } = useToast()

  const [checkInDate, setCheckInDate] = useState<Date | undefined>(() => parseDate(hotel.checkInDate))
  const [checkOutDate, setCheckOutDate] = useState<Date | undefined>(() => parseDate(hotel.checkOutDate))

  useEffect(() => {
    setCheckInDate(parseDate(hotel.checkInDate))
    setCheckOutDate(parseDate(hotel.checkOutDate))
  }, [hotel.checkInDate, hotel.checkOutDate])
  const initialRoomCount = Math.max(1, hotel.rooms ?? 1)
  const initialGuestCount = Math.max(1, hotel.guests ?? 1)

  const [numberOfRooms, setNumberOfRooms] = useState<number>(initialRoomCount)
  const [numberOfGuests, setNumberOfGuests] = useState<number>(initialGuestCount)
  const [bedType, setBedType] = useState<string>('DOUBLE')
  const [specialRequests, setSpecialRequests] = useState<string>('')
  const [guests, setGuests] = useState<GuestDetails[]>(() => buildGuestList(initialGuestCount))

  // Calculate the number of nights between check-in and check-out dates
  const calculateNights = () => {
    if (!checkInDate || !checkOutDate) return 0;
    const timeDiff = checkOutDate.getTime() - checkInDate.getTime();
    return Math.ceil(timeDiff / (1000 * 3600 * 24));
  };

  const [roomPrice, setRoomPrice] = useState<number>(
    hotel.pricePerNight ?? hotel.price ?? hotel.totalPrice ?? 0
  );
  const [isFetching, setIsFetching] = useState<boolean>(false);

  const numberOfNights = Math.max(1, calculateNights());
  const totalRoomPrice = roomPrice * numberOfNights * numberOfRooms;
  const previousTotalRef = useRef<number>(totalRoomPrice);
  const initializedPriceRef = useRef<boolean>(false);
  const skipNextPriceToastRef = useRef<boolean>(false);
  const skipNextGuestToastRef = useRef<boolean>(false);
  const initialGuestCountRef = useRef<number>(initialGuestCount);

  useEffect(() => {
    setRoomPrice(hotel.pricePerNight ?? hotel.price ?? hotel.totalPrice ?? 0)
  }, [hotel.pricePerNight, hotel.price, hotel.totalPrice])

  useEffect(() => {
    setGuests((prev) => buildGuestList(numberOfGuests, prev))
  }, [numberOfGuests])

  useEffect(() => {
    const nextRooms = Math.max(1, hotel.rooms ?? 1)
    const nextGuests = Math.max(1, hotel.guests ?? 1)
    initialGuestCountRef.current = nextGuests
    setNumberOfRooms(nextRooms)
    setNumberOfGuests(nextGuests)
    setGuests((prev) => buildGuestList(nextGuests, prev))
  }, [hotel.rooms, hotel.guests, hotel.id])

  useEffect(() => {
    if (!initializedPriceRef.current) {
      initializedPriceRef.current = true;
      previousTotalRef.current = totalRoomPrice;
      return;
    }

    if (skipNextPriceToastRef.current) {
      skipNextPriceToastRef.current = false;
      previousTotalRef.current = totalRoomPrice;
      return;
    }

    if (previousTotalRef.current !== totalRoomPrice) {
      toast({
        title: "Thông báo thay đổi giá",
        description: `Giá phòng đã thay đổi từ ${previousTotalRef.current.toLocaleString()} VND sang ${totalRoomPrice.toLocaleString()} VND.`,
        duration: 3000,
      });
      previousTotalRef.current = totalRoomPrice;
    }
  }, [totalRoomPrice, toast]);

  // Show toast when number of guests changes
  useEffect(() => {
    if (skipNextGuestToastRef.current) {
      skipNextGuestToastRef.current = false
      return
    }
    if (numberOfGuests === initialGuestCountRef.current) {
      return
    }
    toast({
      title: "Thông báo đặt phòng",
      description: `Số lượng khách đã được cập nhật thành ${numberOfGuests}.`,
      duration: 3000,
    });
  }, [numberOfGuests, toast]);

  // Add the room type functionality
  const [selectedRoomTypeId, setSelectedRoomTypeId] = useState<number>(Number(hotel.roomTypeId) || 1);
  
  // Show toast when room type changes and fetch updated price
  useEffect(() => {
    const initialRoomTypeId = hotel.roomTypeId || hotel.id || 1;
    if (selectedRoomTypeId !== initialRoomTypeId) { // Only when not the initial value
      setIsFetching(true);
      
      import('@/modules/hotel/service').then(module => {
        module.hotelService.getRoomDetails(selectedRoomTypeId)
        .then(updatedRoom => {
          const newTotal = updatedRoom.price * numberOfNights * numberOfRooms || 0;
          skipNextPriceToastRef.current = true;
          previousTotalRef.current = newTotal;
          setRoomPrice(updatedRoom.price);
          toast({
            title: "Thông báo thay đổi giá",
            description: `Giá phòng đã được cập nhật theo loại phòng đã chọn: ${newTotal.toLocaleString()} VND.`,
            duration: 3000,
          });
        })
        .catch(error => {
          console.error("Error fetching updated room price:", error);
          toast({
            title: "Lỗi",
            description: "Không thể cập nhật giá phòng, vui lòng thử lại.",
            variant: "destructive",
          });
        })
        .finally(() => {
          setIsFetching(false);
        });
      });
    }
  }, [selectedRoomTypeId, numberOfNights, numberOfRooms, toast, hotel.roomTypeId, hotel.id]);

  const handleAddGuest = () => {
    setGuests((prev) => {
      if (prev.length >= MAX_GUESTS) {
        return prev
      }
      const next = [...prev, buildGuest('ADDITIONAL')]
      setNumberOfGuests(next.length)
      return next
    })
  }

  const handleRemoveGuest = (index: number) => {
    setGuests((prev) => {
      if (prev.length <= 1) {
        return prev
      }
      const target = prev[index]
      if (!target || target.guestType === 'PRIMARY') {
        return prev
      }
      const next = [...prev]
      next.splice(index, 1)
      setNumberOfGuests(next.length)
      return next
    })
  }

  const handleGuestChange = (index: number, field: keyof GuestDetails, value: string) => {
    const newGuests = [...guests]
    newGuests[index] = {
      ...newGuests[index],
      [field]: value
    }
    setGuests(newGuests)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    
    const sanitizedGuests = guests.map((guest) => ({
      ...guest,
      title: guest.title.trim(),
      firstName: guest.firstName.trim(),
      lastName: guest.lastName.trim(),
      dateOfBirth: guest.dateOfBirth,
      gender: guest.gender,
      nationality: guest.nationality.trim().toUpperCase() || 'VN',
      email: guest.guestType === 'PRIMARY'
        ? (guest.email ?? '').trim()
        : guest.email?.trim(),
      phoneNumber: guest.phoneNumber?.trim(),
      idNumber: guest.idNumber?.trim(),
      loyaltyNumber: guest.loyaltyNumber?.trim(),
      specialRequests: guest.specialRequests?.trim(),
    }))

    const primaryGuest = sanitizedGuests.find((g) => g.guestType === 'PRIMARY')
    const primaryValid = primaryGuest
      && primaryGuest.title
      && primaryGuest.firstName
      && primaryGuest.lastName
      && primaryGuest.dateOfBirth
      && (primaryGuest.email ?? '')
    
    if (!primaryValid || !primaryGuest) {
      alert('Please fill in all required information for the primary guest')
      return
    }

    if (!checkInDate || !checkOutDate) {
      alert('Please select check-in and check-out dates')
      return
    }

    const numberOfNights = Math.max(1, Math.ceil((checkOutDate.getTime() - checkInDate.getTime()) / (1000 * 60 * 60 * 24)))
    const totalPrice = roomPrice * numberOfNights * numberOfRooms

    const normalizeDate = (value: Date | undefined): string => {
      if (!value) return ''
      const year = value.getFullYear()
      const month = String(value.getMonth() + 1).padStart(2, '0')
      const day = String(value.getDate()).padStart(2, '0')
      return `${year}-${month}-${day}`
    }

    const additionalGuests = sanitizedGuests
      .filter((guest) => guest.guestType === 'ADDITIONAL')
      .filter((guest) => guest.title && guest.firstName && guest.lastName && guest.dateOfBirth)

    const finalGuests: GuestDetails[] = [primaryGuest, ...additionalGuests]

    if (finalGuests.length !== sanitizedGuests.length) {
      setGuests(finalGuests)
    }

    if (finalGuests.length !== numberOfGuests) {
      skipNextGuestToastRef.current = true
      setNumberOfGuests(finalGuests.length)
    }

    const bookingDetails: HotelBookingDetails = {
      hotelId: hotel.id, // Correctly assign hotelId
      hotelName: hotel.name,
      hotelAddress: hotel.address,
      city: hotel.city,
      country: hotel.country,
      hotelLatitude: hotel.hotelLatitude ?? hotel.latitude,
      hotelLongitude: hotel.hotelLongitude ?? hotel.longitude,
      starRating: hotel.rating,
      roomTypeId: selectedRoomTypeId, // Use state, remove incorrect fallback
      roomId: hotel.roomId,
      roomType: hotel.roomType,
      roomName: hotel.roomName,
      checkInDate: normalizeDate(checkInDate),
      checkOutDate: normalizeDate(checkOutDate),
      numberOfNights,
      numberOfRooms,
      numberOfGuests: finalGuests.length,
      guests: finalGuests,
      pricePerNight: roomPrice,
      totalRoomPrice: totalPrice,
      bedType,
      amenities: hotel.amenities,
      specialRequests: specialRequests.trim(),
      hotelImage: hotel.image,
      roomImage: hotel.roomImages?.[0] || hotel.image,
      roomImages: hotel.roomImages ?? (hotel.images ? [...hotel.images] : undefined)
    }

    onSubmit(bookingDetails)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Thông tin đặt phòng khách sạn</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Hotel Information */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 p-4 bg-muted rounded-lg">
            <div>
              <h3 className="font-semibold">{hotel.name}</h3>
              <p className="text-sm text-muted-foreground">
                {hotel.address}, {hotel.city}, {hotel.country}
              </p>
              <div className="flex items-center mt-2">
                {[...Array(hotel.rating || 0)].map((_, i) => (
                  <span key={i} className="text-yellow-500">★</span>
                ))}
              </div>
            </div>
            <div>
              <p className="text-sm">
                <span className="font-medium">Room:</span> {hotel.roomName} ({hotel.roomType})
              </p>
              <p className="text-sm">
                <span className="font-medium">Giá mỗi đêm:</span> {roomPrice.toLocaleString()} VND
              </p>
              <p className="text-sm">
                <span className="font-medium">Số đêm:</span> {numberOfNights}
              </p>
              <p className="text-sm">
                <span className="font-medium">Tổng tạm tính:</span> {totalRoomPrice.toLocaleString()} VND
              </p>
            </div>
          </div>

          {/* Booking Options */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <Label htmlFor="checkInDate">Ngày nhận phòng</Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button
                    variant={"outline"}
                    className={cn(
                      "w-full justify-start text-left font-normal",
                      !checkInDate && "text-muted-foreground"
                    )}
                  >
                    <CalendarIcon className="mr-2 h-4 w-4" />
                    {checkInDate ? formatDateOnly(checkInDate.toISOString()) : <span>Chọn ngày</span>}
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0">
                  <Calendar
                    mode="single"
                    selected={checkInDate}
                    onSelect={setCheckInDate}
                    initialFocus
                  />
                </PopoverContent>
              </Popover>
            </div>

            <div>
              <Label htmlFor="checkOutDate">Ngày trả phòng</Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button
                    variant={"outline"}
                    className={cn(
                      "w-full justify-start text-left font-normal",
                      !checkOutDate && "text-muted-foreground"
                    )}
                  >
                    <CalendarIcon className="mr-2 h-4 w-4" />
                    {checkOutDate ? formatDateOnly(checkOutDate.toISOString()) : <span>Chọn ngày</span>}
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0">
                  <Calendar
                    mode="single"
                    selected={checkOutDate}
                    onSelect={setCheckOutDate}
                    initialFocus
                  />
                </PopoverContent>
              </Popover>
            </div>

            <div>
              <Label htmlFor="numberOfRooms">Số lượng phòng</Label>
              <Select
                value={numberOfRooms.toString()} 
                onValueChange={(value) => setNumberOfRooms(parseInt(value))}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Chọn số phòng" />
                </SelectTrigger>
                <SelectContent>
                  {[1, 2, 3, 4, 5].map(num => (
                    <SelectItem key={num} value={num.toString()}>
                      {num} {num === 1 ? 'Phòng' : 'Phòng'}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label htmlFor="numberOfGuests">Số lượng khách</Label>
              <Select
                value={numberOfGuests.toString()} 
                onValueChange={(value) => setNumberOfGuests(parseInt(value))}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Chọn số khách" />
                </SelectTrigger>
                <SelectContent>
                  {Array.from({ length: MAX_GUESTS }, (_, index) => index + 1).map((num) => (
                    <SelectItem key={num} value={num.toString()}>
                      {num} {num === 1 ? 'Khách' : 'Khách'}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label htmlFor="bedType">Loại giường</Label>
              <Select value={bedType} onValueChange={setBedType}>
                <SelectTrigger>
                  <SelectValue placeholder="Chọn loại giường" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="SINGLE">Giường đơn</SelectItem>
                  <SelectItem value="DOUBLE">Giường đôi</SelectItem>
                  <SelectItem value="TWIN">Giường đơn đôi</SelectItem>
                  <SelectItem value="KING">Giường King</SelectItem>
                  <SelectItem value="QUEEN">Giường Queen</SelectItem>
                </SelectContent>
              </Select>
            </div>
            
            <div>
              <Label htmlFor="roomType">Loại phòng</Label>
              <Select value={selectedRoomTypeId?.toString() || (hotel.roomTypeId?.toString() || hotel.id?.toString() || '1')} onValueChange={(value) => setSelectedRoomTypeId(Number(value))}>
                <SelectTrigger>
                  <SelectValue placeholder="Chọn loại phòng" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={hotel.roomTypeId?.toString() || hotel.id?.toString() || '1'}>
                    {hotel.roomType || 'Phòng hiện tại'}
                  </SelectItem>
                  {/* Add more room types if available */}
                  {hotel.roomTypes && Array.isArray(hotel.roomTypes) && hotel.roomTypes.map((roomType: { id: number; name: string; price: number }) => (
                    <SelectItem key={roomType.id} value={roomType.id?.toString()}>
                      {roomType.name} - {roomType.price?.toLocaleString()} VND/đêm
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Guest Information */}
          <div>
            <h3 className="text-lg font-semibold mb-4">Thông tin khách</h3>
            {guests.map((guest, index) => (
              <div key={index} className="border rounded-lg p-4 mb-4">
                <div className="flex justify-between items-center mb-3">
                  <h4 className="font-medium">
                    {guest.guestType === 'PRIMARY' ? 'Khách chính (Liên hệ)' : `Khách ${index}`}
                    {guest.guestType === 'PRIMARY' && <span className="text-sm text-muted-foreground ml-2">(Người liên hệ)</span>}
                  </h4>
                  {guest.guestType !== 'PRIMARY' && guests.length > 1 && (
                    <Button 
                      type="button" 
                      variant="outline" 
                      size="sm" 
                      onClick={() => handleRemoveGuest(index)}
                    >
                      Xóa
                    </Button>
                  )}
                </div>
                
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <Label htmlFor={`title-${index}`}>Danh xưng *</Label>
                    <Select
                      value={guest.title} 
                      onValueChange={(value) => handleGuestChange(index, 'title', value)}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Chọn danh xưng" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="MR">Ông</SelectItem>
                        <SelectItem value="MRS">Bà</SelectItem>
                        <SelectItem value="MS">Cô</SelectItem>
                        <SelectItem value="MISS">Chị</SelectItem>
                        <SelectItem value="DR">Tiến sĩ</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <div>
                    <Label htmlFor={`firstName-${index}`}>Tên *</Label>
                    <Input
                      id={`firstName-${index}`}
                      value={guest.firstName}
                      onChange={(e) => handleGuestChange(index, 'firstName', e.target.value)}
                      placeholder="Tên"
                    />
                  </div>

                  <div>
                    <Label htmlFor={`lastName-${index}`}>Họ *</Label>
                    <Input
                      id={`lastName-${index}`}
                      value={guest.lastName}
                      onChange={(e) => handleGuestChange(index, 'lastName', e.target.value)}
                      placeholder="Họ"
                    />
                  </div>

                  <div>
                    <Label htmlFor={`dateOfBirth-${index}`}>Ngày sinh *</Label>
                    <Input
                      id={`dateOfBirth-${index}`}
                      type="date"
                      value={guest.dateOfBirth}
                      onChange={(e) => handleGuestChange(index, 'dateOfBirth', e.target.value)}
                    />
                  </div>

                  <div>
                    <Label htmlFor={`gender-${index}`}>Giới tính *</Label>
                    <Select
                      value={guest.gender} 
                      onValueChange={(value) => handleGuestChange(index, 'gender', value as any)}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Chọn giới tính" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="M">Nam</SelectItem>
                        <SelectItem value="F">Nữ</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <div>
                    <Label htmlFor={`nationality-${index}`}>Quốc tịch *</Label>
                    <Input
                      id={`nationality-${index}`}
                      value={guest.nationality}
                      onChange={(e) => handleGuestChange(index, 'nationality', e.target.value)}
                      placeholder="Mã quốc gia (vd: VN)"
                    />
                  </div>

                  {guest.guestType === 'PRIMARY' && (
                    <>
                      <div>
                        <Label htmlFor={`email-${index}`}>Email *</Label>
                        <Input
                          id={`email-${index}`}
                          type="email"
                          value={guest.email}
                          onChange={(e) => handleGuestChange(index, 'email', e.target.value)}
                          placeholder="Địa chỉ email"
                        />
                      </div>

                      <div>
                        <Label htmlFor={`phoneNumber-${index}`}>Số điện thoại</Label>
                        <Input
                          id={`phoneNumber-${index}`}
                          value={guest.phoneNumber}
                          onChange={(e) => handleGuestChange(index, 'phoneNumber', e.target.value)}
                          placeholder="Số điện thoại"
                        />
                      </div>
                    </>
                  )}
                </div>
              </div>
            ))}

            {guests.length < MAX_GUESTS && (
              <Button 
                type="button" 
                variant="outline" 
                onClick={handleAddGuest}
                className="mt-2"
              >
                Thêm khách khác
              </Button>
            )}
          </div>

          {/* Special Requests */}
          <div>
            <Label htmlFor="specialRequests">Yêu cầu đặc biệt</Label>
            <Textarea
              id="specialRequests"
              value={specialRequests}
              onChange={(e) => setSpecialRequests(e.target.value)}
              placeholder="Các yêu cầu hoặc điều kiện đặc biệt"
              rows={3}
            />
          </div>

          {/* Actions */}
          <div className="flex justify-between">
            <Button type="button" variant="outline" onClick={onCancel}>
              Hủy
            </Button>
            <Button type="submit">
              Tiếp tục
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
