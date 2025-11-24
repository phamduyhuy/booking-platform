"use client";

import type { JSX } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import Image from "next/image";
import {
  bookingService,
  type BookingHistoryItemDto,
  type BookingHistoryResponseDto,
} from "@/modules/booking/service";
import { flightService } from "@/modules/flight/service";
import type { FlightFareDetails } from "@/modules/flight/type";
import type { RoomDetails } from "@/modules/hotel/type";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Loader2,
  Calendar,
  Plane,
  Building2,
  MapPin,
  Layers,
  CheckCircle2,
  Clock3,
  XCircle,
  AlertTriangle,
} from "lucide-react";
import { useDateFormatter } from "@/hooks/use-date-formatter";
import { useCurrencyFormatter } from "@/hooks/use-currency-formatter";
import { useRouter } from "next/navigation";
import { useToast } from "@/hooks/use-toast";
import { useRecommendPanel } from "@/contexts/recommend-panel-context";
import { mapboxService } from "@/modules/mapbox/services/mapboxClientService";
import { env } from "@/env.mjs";
import { RefundDialog } from "./RefundDialog";
import { paymentService } from "@/modules/payment/service";

const RESUME_STORAGE_KEY = "bookingResumePayload";
const PROCESSING_STATUSES = new Set([
  "PENDING",
  "VALIDATION_PENDING",
  "PAYMENT_PENDING",
]);
const DEFAULT_MAPBOX_STYLE = "mapbox://styles/mapbox/streets-v12";

const PAGE_SIZE = 10;

const STATUS_BADGE_MAP: Record<
  string,
  { label: string; className: string; icon: JSX.Element }
> = {
  CONFIRMED: {
    label: "Đã xác nhận",
    className: "bg-green-500/10 text-green-400",
    icon: <CheckCircle2 className="h-4 w-4" />,
  },
  PAID: {
    label: "Đã thanh toán",
    className: "bg-green-500/10 text-green-400",
    icon: <CheckCircle2 className="h-4 w-4" />,
  },
  PENDING: {
    label: "Đang xử lý",
    className: "bg-amber-500/10 text-amber-400",
    icon: <Clock3 className="h-4 w-4" />,
  },
  PAYMENT_PENDING: {
    label: "Chờ thanh toán",
    className: "bg-amber-500/10 text-amber-400",
    icon: <Clock3 className="h-4 w-4" />,
  },
  VALIDATION_PENDING: {
    label: "Đang xác thực",
    className: "bg-blue-500/10 text-blue-400",
    icon: <Clock3 className="h-4 w-4" />,
  },
  CANCELLED: {
    label: "Đã hủy",
    className: "bg-red-500/10 text-red-400",
    icon: <XCircle className="h-4 w-4" />,
  },
  CANCELED: {
    label: "Đã hủy",
    className: "bg-red-500/10 text-red-400",
    icon: <XCircle className="h-4 w-4" />,
  },
  FAILED: {
    label: "Thất bại",
    className: "bg-red-500/10 text-red-400",
    icon: <XCircle className="h-4 w-4" />,
  },
  PAYMENT_FAILED: {
    label: "Thanh toán thất bại",
    className: "bg-red-500/10 text-red-400",
    icon: <AlertTriangle className="h-4 w-4" />,
  },
  VALIDATION_FAILED: {
    label: "Xác thực thất bại",
    className: "bg-red-500/10 text-red-400",
    icon: <AlertTriangle className="h-4 w-4" />,
  },
};

const getBookingIcon = (type?: string) => {
  switch (type) {
    case "FLIGHT":
      return <Plane className="h-5 w-5" />;
    case "HOTEL":
      return <Building2 className="h-5 w-5" />;
    case "COMBO":
      return <Layers className="h-5 w-5" />;
    default:
      return <MapPin className="h-5 w-5" />;
  }
};

const normalizeIsoDateTime = (value?: string | null) => {
  if (!value) return null;
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(value)) {
    return `${value}Z`;
  }
  return value;
};

const ensureSeatClass = (value?: string | null) => {
  if (!value) return "ECONOMY";
  return value.toUpperCase();
};

const resolveNumber = (value?: number | string | null): number | undefined => {
  if (value == null) return undefined;
  const numeric = typeof value === "string" ? Number(value) : value;
  return Number.isFinite(numeric) ? numeric : undefined;
};

const buildStaticMapUrl = (
  lat?: number | string | null,
  lng?: number | string | null
) => {
  const latitude = resolveNumber(lat);
  const longitude = resolveNumber(lng);
  const token = env.NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN;

  if (!token || latitude === undefined || longitude === undefined) {
    return undefined;
  }

  const style = "mapbox/streets-v12";
  const markerColor = "1967D2";
  return `https://api.mapbox.com/styles/v1/${style}/static/pin-s+${markerColor}(${longitude},${latitude})/${longitude},${latitude},10,0/600x360@2x?access_token=${token}`;
};

const parseProductDetails = (json?: string | null) => {
  if (!json) return null;
  try {
    return JSON.parse(json);
  } catch (err) {
    console.warn("Failed to parse product details", err);
    return null;
  }
};

type BookingDetailState = {
  flight?: FlightFareDetails;
  hotel?: RoomDetails;
  fallbackFlight?: any;
  fallbackHotel?: any;
};

export function BookingHistoryTab() {
  const { formatDateTime, formatDateOnly } = useDateFormatter();
  const { formatCurrency: formatCurrencyWithUserPreference } =
    useCurrencyFormatter();

  const renderStatusBadge = (status?: string | null) => {
    if (!status) return null;
    const meta = STATUS_BADGE_MAP[status] ?? {
      label: status,
      className: "bg-gray-500/10 text-gray-300",
      icon: <Clock3 className="h-4 w-4" />,
    };

    return (
      <Badge className={`gap-1 ${meta.className}`}>
        {meta.icon}
        {meta.label}
      </Badge>
    );
  };

  const renderFlightDetails = (flight?: FlightFareDetails, fallback?: any) => {
    const source = flight ?? fallback;
    if (!source) return null;

    const departureRaw = flight?.departureTime ?? source?.departureDateTime;
    const arrivalRaw = flight?.arrivalTime ?? source?.arrivalDateTime;
    const normalizedDeparture = normalizeIsoDateTime(departureRaw);
    const normalizedArrival = normalizeIsoDateTime(arrivalRaw);
    const departure = normalizedDeparture ?? departureRaw;
    const arrival = normalizedArrival ?? arrivalRaw;
    const seatClass = flight?.seatClass ?? ensureSeatClass(source?.seatClass);
    const priceRaw =
      flight?.price ?? source?.pricePerPassenger ?? source?.totalFlightPrice;
    const price = priceRaw != null ? Number(priceRaw) : undefined;
    const currency = flight?.currency ?? source?.currency ?? "VND";
    const airline = flight?.airline ?? source?.airline;
    const flightNumber = flight?.flightNumber ?? source?.flightNumber;
    const origin =
      flight?.originAirport ?? source?.originAirport ?? source?.origin;
    const destination =
      flight?.destinationAirport ??
      source?.destinationAirport ??
      source?.destination;
    const originLabel = source?.originAirportName ?? origin;
    const destinationLabel = source?.destinationAirportName ?? destination;
    const availableSeatsRaw = flight?.availableSeats ?? source?.availableSeats;
    const availableSeats =
      availableSeatsRaw != null ? Number(availableSeatsRaw) : null;
    const airlineLogo =
      flight?.airlineLogo ??
      source?.airlineLogo ??
      fallback?.logo ??
      "/airplane-generic.png";

    const originLatitude = resolveNumber(
      flight?.originLatitude ?? source?.originLatitude
    );
    const originLongitude = resolveNumber(
      flight?.originLongitude ?? source?.originLongitude
    );
    const destinationLatitude = resolveNumber(
      flight?.destinationLatitude ?? source?.destinationLatitude
    );
    const destinationLongitude = resolveNumber(
      flight?.destinationLongitude ?? source?.destinationLongitude
    );

    const originPreview =
      source?.originAirportImage ??
      buildStaticMapUrl(originLatitude, originLongitude);
    const destinationPreview =
      source?.destinationAirportImage ??
      buildStaticMapUrl(destinationLatitude, destinationLongitude);

    return (
      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white/80 shadow-sm">
        <div className="flex flex-col gap-4 p-4">
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="relative h-16 w-16 overflow-hidden rounded-full border border-gray-200 bg-white">
                <Image
                  src={airlineLogo || "/airplane-generic.png"}
                  alt={airline || "Airline"}
                  fill
                  className="object-contain p-2"
                  unoptimized
                />
              </div>
              <div>
                <p className="text-xs uppercase text-gray-500">Chuyến bay</p>
                <p className="text-lg font-semibold text-gray-900">
                  {airline || "Không xác định"}
                </p>
                {flightNumber && (
                  <p className="text-sm text-gray-500">{flightNumber}</p>
                )}
              </div>
            </div>
            <div className="text-right">
              <p className="text-xs uppercase text-gray-500">Giá vé</p>
              <p className="text-base font-semibold text-gray-900">
                {formatCurrencyWithUserPreference(price ?? priceRaw, currency)}
              </p>
              {availableSeats != null && Number.isFinite(availableSeats) && (
                <p className="text-xs text-gray-500">
                  Số ghế trống: {availableSeats}
                </p>
              )}
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
              {originPreview ? (
                <div className="relative h-32 w-full">
                  <Image
                    src={originPreview}
                    alt={`Bản đồ ${originLabel || "khởi hành"}`}
                    fill
                    className="object-cover"
                    unoptimized
                  />
                </div>
              ) : (
                <div className="flex h-32 w-full items-center justify-center bg-gray-100 text-gray-400">
                  <MapPin className="h-8 w-8" />
                </div>
              )}
              <div className="p-3">
                <p className="text-xs uppercase text-gray-500">Khởi hành</p>
                <p className="font-medium text-gray-900">
                  {originLabel || "—"}
                </p>
                <p className="text-sm text-gray-500">
                  {formatDateTime(departure)}
                </p>
              </div>
            </div>
            <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
              {destinationPreview ? (
                <div className="relative h-32 w-full">
                  <Image
                    src={destinationPreview}
                    alt={`Bản đồ ${destinationLabel || "điểm đến"}`}
                    fill
                    className="object-cover"
                    unoptimized
                  />
                </div>
              ) : (
                <div className="flex h-32 w-full items-center justify-center bg-gray-100 text-gray-400">
                  <MapPin className="h-8 w-8" />
                </div>
              )}
              <div className="p-3">
                <p className="text-xs uppercase text-gray-500">Điểm đến</p>
                <p className="font-medium text-gray-900">
                  {destinationLabel || "—"}
                </p>
                <p className="text-sm text-gray-500">
                  {formatDateTime(arrival)}
                </p>
              </div>
            </div>
          </div>

          <div className="grid gap-3 text-sm text-gray-600 md:grid-cols-3">
            <div>
              <span className="text-xs uppercase text-gray-500">
                Hành trình
              </span>
              <p className="font-medium text-gray-900">
                {origin || "—"} → {destination || "—"}
              </p>
            </div>
            <div>
              <span className="text-xs uppercase text-gray-500">Hạng ghế</span>
              <p className="font-medium text-gray-900">{seatClass}</p>
            </div>
            <div>
              <span className="text-xs uppercase text-gray-500">Ngày bay</span>
              <p className="font-medium text-gray-900">
                {formatDateOnly(departure)}
              </p>
            </div>
          </div>
        </div>
      </div>
    );
  };

  const renderHotelDetails = (hotel?: RoomDetails, fallback?: any) => {
    const source = hotel ?? fallback;
    if (!source) return null;

    const roomTitle =
      hotel?.roomType?.name ?? source?.roomName ?? source?.roomType;
    const description = hotel?.description ?? source?.description;
    const priceRaw =
      hotel?.price ?? source?.pricePerNight ?? source?.totalRoomPrice;
    const price = priceRaw != null ? Number(priceRaw) : undefined;
    const currency = source?.currency ?? "VND";
    const capacity = hotel?.maxOccupancy ?? source?.numberOfGuests;
    const bedType = hotel?.bedType ?? source?.bedType;
    const roomNumber = hotel?.roomNumber ?? source?.roomId;
    const hotelName = source?.hotelName ?? fallback?.hotelName;
    const address =
      source?.hotelAddress ??
      [source?.city, source?.country].filter(Boolean).join(", ");

    const primaryHotelImage =
      source?.hotelImage ??
      hotel?.primaryImage?.url ??
      (Array.isArray(hotel?.media)
        ? hotel.media.find((item) => item?.url)?.url
        : undefined) ??
      source?.image ??
      (Array.isArray(source?.images) ? source.images.find(Boolean) : undefined);

    const roomImageCandidates: string[] = [];
    if (hotel?.primaryImage?.url) {
      roomImageCandidates.push(hotel.primaryImage.url);
    }
    if (Array.isArray(hotel?.media)) {
      hotel.media.forEach((item) => {
        if (item?.url) {
          roomImageCandidates.push(item.url);
        }
      });
    }
    if (Array.isArray(source?.roomImages)) {
      source.roomImages.forEach((img: string) => {
        if (img) {
          roomImageCandidates.push(img);
        }
      });
    }
    if (source?.roomImage) {
      roomImageCandidates.push(source.roomImage);
    }

    const roomImages = Array.from(new Set(roomImageCandidates.filter(Boolean)));
    const primaryRoomImage = roomImages[0] ?? primaryHotelImage;

    return (
      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white/80 shadow-sm">
        {primaryHotelImage && (
          <div className="relative h-40 w-full">
            <Image
              src={primaryHotelImage}
              alt={hotelName || "Khách sạn"}
              fill
              className="object-cover"
              unoptimized
            />
          </div>
        )}
        <div className="flex flex-col gap-4 p-4">
          <div className="flex flex-col gap-1">
            <p className="text-xs uppercase text-gray-500">Khách sạn</p>
            <p className="text-lg font-semibold text-gray-900">
              {hotelName || "Thông tin khách sạn"}
            </p>
            {address && <p className="text-sm text-gray-500">{address}</p>}
          </div>

          {primaryRoomImage && (
            <div>
              <p className="text-xs uppercase text-gray-500">Phòng đã đặt</p>
              <div className="relative mt-2 h-32 w-full overflow-hidden rounded-lg border border-gray-200">
                <Image
                  src={primaryRoomImage}
                  alt={roomTitle || "Phòng"}
                  fill
                  className="object-cover"
                  unoptimized
                />
              </div>
            </div>
          )}

          {roomImages.length > 1 && (
            <div className="flex gap-2 overflow-x-auto">
              {roomImages.slice(0, 5).map((imageUrl, index) => (
                <div
                  key={`${imageUrl}-${index}`}
                  className="relative h-20 w-28 shrink-0 overflow-hidden rounded-md border border-gray-200"
                >
                  <Image
                    src={imageUrl}
                    alt={`Ảnh phòng ${index + 1}`}
                    fill
                    className="object-cover"
                    unoptimized
                  />
                </div>
              ))}
            </div>
          )}

          <div className="grid gap-3 text-sm text-gray-600 md:grid-cols-2">
            <div>
              <span className="text-xs uppercase text-gray-500">Phòng</span>
              <p className="font-medium text-gray-900">
                {roomTitle || "Phòng"}
              </p>
              {roomNumber && (
                <p className="text-xs text-gray-500">Mã phòng: {roomNumber}</p>
              )}
            </div>
            <div>
              <span className="text-xs uppercase text-gray-500">Giá</span>
              <p className="font-medium text-gray-900">
                {formatCurrencyWithUserPreference(price ?? priceRaw, currency)}
              </p>
            </div>
            <div>
              <span className="text-xs uppercase text-gray-500">Sức chứa</span>
              <p className="font-medium text-gray-900">
                {capacity ? `${capacity} khách` : "—"}
              </p>
            </div>
            <div>
              <span className="text-xs uppercase text-gray-500">
                Loại giường
              </span>
              <p className="font-medium text-gray-900">{bedType || "—"}</p>
            </div>
          </div>

          {description && (
            <p className="text-sm text-gray-600/80">{description}</p>
          )}
        </div>
      </div>
    );
  };

  const renderFallbackFlightDetails = (fallback: any) =>
    renderFlightDetails(undefined, fallback);
  const renderFallbackHotelDetails = (fallback: any) =>
    renderHotelDetails(undefined, fallback);
  const [items, setItems] = useState<BookingHistoryItemDto[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [detailLoadingId, setDetailLoadingId] = useState<string | null>(null);
  const [detailErrors, setDetailErrors] = useState<
    Record<string, string | null>
  >({});
  const [detailMap, setDetailMap] = useState<
    Record<string, BookingDetailState>
  >({});
  const router = useRouter();
  const { toast } = useToast();
  const { showLocation, showLocations, showJourney, setMapStyle, mapStyle } =
    useRecommendPanel();
  const [now, setNow] = useState(() => Date.now());
  const [refundDialogOpen, setRefundDialogOpen] = useState(false);
  const [refundPaymentData, setRefundPaymentData] = useState<{
    bookingId: string;
    paymentIntentId: string;
    transactionId: string;
    amount: number;
    currency: string;
  } | null>(null);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const formatCountdown = useCallback(
    (expires?: string | null) => {
      if (!expires) return null;
      const target = new Date(expires).getTime();
      if (Number.isNaN(target)) return null;
      const remaining = target - now;
      if (remaining <= 0) return "00:00";
      const totalSeconds = Math.floor(remaining / 1000);
      const minutes = Math.floor(totalSeconds / 60);
      const seconds = totalSeconds % 60;
      return `${minutes.toString().padStart(2, "0")}:${seconds
        .toString()
        .padStart(2, "0")}`;
    },
    [now]
  );

  const ensureDefaultMapStyle = useCallback(() => {
    if (mapStyle !== DEFAULT_MAPBOX_STYLE) {
      setMapStyle(DEFAULT_MAPBOX_STYLE);
    }
  }, [mapStyle, setMapStyle]);

  const handleShowFlightLocation = useCallback(
    (booking: BookingHistoryItemDto, target: "origin" | "destination") => {
      ensureDefaultMapStyle();
      const latValue =
        target === "origin"
          ? booking.originLatitude
          : booking.destinationLatitude;
      const lngValue =
        target === "origin"
          ? booking.originLongitude
          : booking.destinationLongitude;

      const lat = typeof latValue === "string" ? Number(latValue) : latValue;
      const lng = typeof lngValue === "string" ? Number(lngValue) : lngValue;

      if (!Number.isFinite(lat ?? NaN) || !Number.isFinite(lng ?? NaN)) {
        toast({
          title: "Vị trí không có sẵn",
          description: `Sân bay ${
            target === "origin" ? "khởi hành" : "đến"
          } chưa được cấu hình tọa độ trong hệ thống.`,
          variant: "destructive",
        });
        return;
      }

      showLocation({
        lat: lat as number,
        lng: lng as number,
        title: `${target === "origin" ? "Khởi hành" : "Đến"} • ${
          booking.bookingReference
        }`,
        description: booking.productSummary ?? undefined,
        type: "airport",
      });
    },
    [showLocation, toast, ensureDefaultMapStyle]
  );

  const handleShowHotelLocation = useCallback(
    (booking: BookingHistoryItemDto) => {
      ensureDefaultMapStyle();
      const latValue = booking.hotelLatitude;
      const lngValue = booking.hotelLongitude;
      const lat = typeof latValue === "string" ? Number(latValue) : latValue;
      const lng = typeof lngValue === "string" ? Number(lngValue) : lngValue;

      if (!Number.isFinite(lat ?? NaN) || !Number.isFinite(lng ?? NaN)) {
        toast({
          title: "Vị trí không có sẵn",
          description: "Vị trí khách sạn chưa được cấu hình trong hệ thống.",
          variant: "destructive",
        });
        return;
      }

      showLocation({
        lat: lat as number,
        lng: lng as number,
        title: `Khách sạn • ${booking.bookingReference}`,
        description: booking.productSummary ?? undefined,
        type: "hotel",
      });
    },
    [showLocation, toast, ensureDefaultMapStyle]
  );

  const handleShowFlightJourney = useCallback(
    (booking: BookingHistoryItemDto) => {
      const originLat =
        typeof booking.originLatitude === "string"
          ? Number(booking.originLatitude)
          : booking.originLatitude;
      const originLng =
        typeof booking.originLongitude === "string"
          ? Number(booking.originLongitude)
          : booking.originLongitude;
      const destLat =
        typeof booking.destinationLatitude === "string"
          ? Number(booking.destinationLatitude)
          : booking.destinationLatitude;
      const destLng =
        typeof booking.destinationLongitude === "string"
          ? Number(booking.destinationLongitude)
          : booking.destinationLongitude;

      if (!originLat || !originLng || !destLat || !destLng) {
        toast({
          title: "Hành trình không có sẵn",
          description:
            "Đặt vé máy bay này không có đủ dữ liệu tọa độ để hiển thị hành trình.",
          variant: "destructive",
        });
        return;
      }

      ensureDefaultMapStyle();
      const originLabel = booking.originAirportCode || booking.originCity || "";
      const destinationLabel =
        booking.destinationAirportCode || booking.destinationCity || "";
      const markerLabel =
        booking.bookingReference ??
        (originLabel && destinationLabel
          ? `${originLabel} → ${destinationLabel}`
          : "Hành trình");

      const pathCoordinates = mapboxService.generateFlightPath(
        { latitude: originLat, longitude: originLng },
        { latitude: destLat, longitude: destLng }
      );

      const resolvedCoordinates =
        pathCoordinates.length > 1
          ? pathCoordinates
          : ([
              [originLng, originLat],
              [destLng, destLat],
            ] as [number, number][]);

      showJourney({
        id: booking.bookingId,
        origin: { latitude: originLat, longitude: originLng },
        destination: { latitude: destLat, longitude: destLng },
        color: "#ef4444",
        travelMode: "flight",
        animate: true,
        markerLabel,
        pathCoordinates: resolvedCoordinates,
        durationMs: Math.max(8000, resolvedCoordinates.length * 22),
      });

      showLocations(
        [
          {
            id: `${booking.bookingId}-origin`,
            lat: originLat,
            lng: originLng,
            title: originLabel
              ? `Khởi hành: ${originLabel}`
              : `Khởi hành • ${booking.bookingReference}`,
            description: booking.productSummary ?? undefined,
            type: "airport",
          },
          {
            id: `${booking.bookingId}-destination`,
            lat: destLat,
            lng: destLng,
            title: destinationLabel
              ? `Đến: ${destinationLabel}`
              : `Đến • ${booking.bookingReference}`,
            description: booking.productSummary ?? undefined,
            type: "airport",
          },
        ],
        { preserveJourneys: true }
      );
    },
    [showJourney, showLocations, toast, ensureDefaultMapStyle]
  );

  const handleContinueBooking = useCallback(
    (booking: BookingHistoryItemDto) => {
      try {
        // For all bookings, including those requiring payment, use the resume flow
        // The resumeBooking function in booking context will handle setting the correct step based on status and sagaState
        const productDetails = booking.productDetailsJson
          ? JSON.parse(booking.productDetailsJson)
          : null;
        const payload = { booking, productDetails };

        // Store in sessionStorage with a consistent key
        sessionStorage.setItem("bookingResumePayload", JSON.stringify(payload));

        // Redirect to homepage with resume parameter - this will open the booking modal at the appropriate step
        router.push(`/?resume=${booking.bookingId}`);
      } catch (error) {
        console.error("Không thể chuẩn bị đặt chỗ để tiếp tục", error);
        toast({
          title: "Không thể tiếp tục đặt chỗ",
          description: "Vui lòng thử lại sau.",
          variant: "destructive",
        });
      }
    },
    [router, toast]
  );

  const handleRequestRefund = async (booking: BookingHistoryItemDto) => {
    try {
      setIsLoading(true);
      const summary = await paymentService.getPaymentSummary(booking.bookingId);

      if (summary && summary.transactionId) {
        setRefundPaymentData({
          bookingId: booking.bookingId,
          paymentIntentId: summary.paymentId, // Assuming paymentId maps to paymentIntentId or gateway ID
          transactionId: summary.transactionId,
          amount: summary.amount,
          currency: summary.currency,
        });
        setRefundDialogOpen(true);
      } else {
        toast({
          variant: "destructive",
          title: "Không thể hoàn tiền",
          description:
            "Không tìm thấy thông tin giao dịch hợp lệ để hoàn tiền.",
        });
      }
    } catch (error) {
      console.error("Failed to get payment summary", error);
      toast({
        variant: "destructive",
        title: "Lỗi",
        description: "Không thể tải thông tin thanh toán.",
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;

    async function loadHistory() {
      setIsLoading(true);
      setError(null);
      try {
        const response: BookingHistoryResponseDto =
          await bookingService.history(page, PAGE_SIZE);
        if (cancelled) return;
        setHasNext(response.hasNext);
        setItems((prev) =>
          page === 0 ? response.items : [...prev, ...response.items]
        );
      } catch (err: any) {
        if (cancelled) return;
        console.error("Failed to load booking history", err);
        setError(err?.message ?? "Failed to load booking history");
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    loadHistory();
    return () => {
      cancelled = true;
    };
  }, [page]);

  const handleRetry = () => {
    setItems([]);
    setDetailMap({});
    setDetailErrors({});
    setExpandedId(null);
    setPage(0);
  };

  const handleLoadMore = () => {
    if (!isLoading && hasNext) {
      setPage((prev) => prev + 1);
    }
  };

  const handleViewDetails = async (booking: BookingHistoryItemDto) => {
    const bookingId = booking.bookingId;

    if (expandedId === bookingId) {
      setExpandedId(null);
      return;
    }

    setExpandedId(bookingId);

    if (detailMap[bookingId]) {
      return;
    }

    const product = parseProductDetails(booking.productDetailsJson);
    if (!product) {
      setDetailErrors((prev) => ({
        ...prev,
        [bookingId]: "No product details available for this booking.",
      }));
      return;
    }

    const baseDetail: BookingDetailState = {
      fallbackFlight:
        booking.bookingType === "COMBO"
          ? product?.flightDetails
          : booking.bookingType === "FLIGHT"
          ? product
          : undefined,
      fallbackHotel:
        booking.bookingType === "COMBO"
          ? product?.hotelDetails
          : booking.bookingType === "HOTEL"
          ? product
          : undefined,
    };

    setDetailMap((prev) => ({ ...prev, [bookingId]: baseDetail }));
    setDetailErrors((prev) => ({ ...prev, [bookingId]: null }));
    setDetailLoadingId(bookingId);

    try {
      const detail: BookingDetailState = { ...baseDetail };

      if (booking.bookingType === "FLIGHT" || booking.bookingType === "COMBO") {
        const flightInfo =
          booking.bookingType === "COMBO" ? product?.flightDetails : product;
        if (
          flightInfo?.flightId &&
          flightInfo?.seatClass &&
          flightInfo?.departureDateTime
        ) {
          const fareDetails = await flightService.getFareDetails(
            flightInfo.flightId,
            {
              seatClass: ensureSeatClass(flightInfo.seatClass),
              departureDateTime: flightInfo.departureDateTime,
              scheduleId: (flightInfo as any).scheduleId,
              fareId: (flightInfo as any).fareId,
            }
          );
          // Cast to FlightFareDetails since getFareDetails returns FlightDetails
          detail.flight = fareDetails as any as FlightFareDetails;
        }
      }

      // For hotel bookings we rely on the stored booking payload. Additional API calls can be added here when richer endpoints exist.

      setDetailMap((prev) => ({ ...prev, [bookingId]: detail }));
    } catch (err: any) {
      console.error("Failed to load booking item details", err);
      setDetailErrors((prev) => ({
        ...prev,
        [bookingId]: err?.message ?? "Failed to load booking details.",
      }));
    } finally {
      setDetailLoadingId(null);
    }
  };

  const historyByDate = useMemo(() => {
    if (items.length === 0) {
      return [];
    }

    return items.reduce<{ date: string; entries: BookingHistoryItemDto[] }[]>(
      (acc, item) => {
        const key = item.createdAt?.split("T")[0] ?? "Unknown";
        const group = acc.find((g) => g.date === key);
        if (group) {
          group.entries.push(item);
        } else {
          acc.push({ date: key, entries: [item] });
        }
        return acc;
      },
      []
    );
  }, [items]);

  return (
    <div className="space-y-4 h-full flex flex-col">
      <Card className="bg-white/70 border-gray-200 flex-1 flex flex-col min-h-0">
        <CardHeader>
          <CardTitle className="text-gray-900">Lịch sử đặt chỗ</CardTitle>
          <CardDescription>
            Xem lại các đặt chỗ đã hoàn thành và đang thực hiện của bạn
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4 overflow-y-auto">
          {error && (
            <div className="rounded-lg border border-red-500/30 bg-red-50 p-3">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-red-500" />
                <div>
                  <p className="text-sm text-red-600">{error}</p>
                  <Button
                    variant="outline"
                    size="sm"
                    className="mt-2"
                    onClick={handleRetry}
                  >
                    Thử lại
                  </Button>
                </div>
              </div>
            </div>
          )}

          {items.length === 0 && !isLoading && !error && (
            <div className="text-center py-10 bg-gray-50 rounded-lg">
              <Calendar className="h-12 w-12 text-gray-500 mx-auto mb-3" />
              <h3 className="text-lg font-medium text-gray-900 mb-1">
                Chưa có đặt chỗ nào
              </h3>
              <p className="text-gray-600">
                Lên kế hoạch cho chuyến đi tiếp theo của bạn để xem các đặt chỗ
                được liệt kê ở đây.
              </p>
            </div>
          )}

          {historyByDate.map((group) => (
            <div key={group.date} className="space-y-2">
              <div className="text-xs uppercase tracking-wide text-gray-400">
                {formatDateOnly(group.date)}
              </div>
              <div className="space-y-2">
                {group.entries.map((booking) => {
                  const bookingId = booking.bookingId;
                  const detail = detailMap[bookingId];
                  const detailError = detailErrors[bookingId];
                  const isExpanded = expandedId === bookingId;
                  const countdown = formatCountdown(
                    booking.reservationExpiresAt
                  );
                  const isAwaitingPayment =
                    (booking.status || "").toUpperCase() === "PAYMENT_PENDING";
                  const isProcessing = PROCESSING_STATUSES.has(
                    (booking.status || "").toUpperCase()
                  );
                  const hasCountdown = countdown !== null;
                  const isExpired = hasCountdown && countdown === "00:00";
                  const isCompleted =
                    booking.status === "COMPLETED" ||
                    booking.status === "PAID" ||
                    booking.status === "CONFIRMED";

                  return (
                    <div
                      key={bookingId}
                      className="rounded-lg border border-gray-200 bg-gray-50 p-3"
                    >
                      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                        <div className="flex items-start gap-3">
                          <div className="text-cyan-500">
                            {getBookingIcon(booking.bookingType)}
                          </div>
                          <div>
                            <div className="flex flex-wrap items-center gap-1">
                              <h3 className="text-gray-900 font-medium">
                                {booking.productSummary ??
                                  booking.bookingType ??
                                  "Đặt chỗ"}
                              </h3>
                              {renderStatusBadge(booking.status)}
                            </div>
                            <div className="text-sm text-gray-600 space-y-1">
                              <p>{`Mã tham chiếu: ${booking.bookingReference}`}</p>
                              {booking.confirmationNumber && (
                                <p>{`Xác nhận: ${booking.confirmationNumber}`}</p>
                              )}
                              {booking.createdAt && (
                                <p>{`Tạo lúc: ${formatDateTime(
                                  booking.createdAt
                                )}`}</p>
                              )}
                              {booking.updatedAt && (
                                <p>{`Cập nhật lúc: ${formatDateTime(
                                  booking.updatedAt
                                )}`}</p>
                              )}
                              {isProcessing && hasCountdown && (
                                <p
                                  className={`flex items-center gap-1 ${
                                    isExpired
                                      ? "text-red-500"
                                      : "text-amber-600"
                                  }`}
                                >
                                  <Clock3 className="h-4 w-4" />
                                  {isExpired
                                    ? "Giữ chỗ đã hết hạn"
                                    : `Giữ chỗ sẽ hết hạn trong ${countdown}`}
                                </p>
                              )}
                            </div>
                          </div>
                        </div>
                        <div className="flex flex-col items-start gap-1 text-sm md:items-end">
                          <span className="text-gray-900 font-semibold text-base">
                            {formatCurrencyWithUserPreference(
                              booking.totalAmount ?? 0,
                              booking.currency ?? undefined
                            )}
                          </span>
                          <div className="flex gap-1">
                            <Button
                              variant="outline"
                              size="sm"
                              className="border-gray-300 text-gray-700 hover:bg-gray-50"
                              onClick={() => handleViewDetails(booking)}
                              disabled={detailLoadingId === bookingId}
                            >
                              {isExpanded ? "Ẩn chi tiết" : "Xem chi tiết"}
                            </Button>
                          </div>
                        </div>
                      </div>

                      {isExpanded && (
                        <div className="mt-3 space-y-3">
                          {detailLoadingId === bookingId && (
                            <div className="flex items-center gap-1 text-sm text-gray-300">
                              <Loader2 className="h-4 w-4 animate-spin" />
                              Đang tải chi tiết đặt chỗ…
                            </div>
                          )}
                          {detailError && (
                            <div className="flex items-center gap-1 text-sm text-red-400">
                              <AlertTriangle className="h-4 w-4" />
                              {detailError}
                            </div>
                          )}
                          {detail && detailLoadingId !== bookingId && (
                            <div className="space-y-3">
                              {renderFlightDetails(
                                detail.flight,
                                detail.fallbackFlight
                              )}
                              {renderHotelDetails(
                                detail.hotel,
                                detail.fallbackHotel
                              )}
                              {!detail.flight &&
                                !detail.hotel &&
                                !detail.fallbackFlight &&
                                !detail.fallbackHotel && (
                                  <p className="text-sm text-gray-300">
                                    Không có chi tiết bổ sung cho đặt chỗ này.
                                  </p>
                                )}
                            </div>
                          )}
                        </div>
                      )}

                      <div className="mt-3 flex flex-wrap gap-1">
                        {(booking.bookingType === "FLIGHT" ||
                          booking.bookingType === "COMBO") && (
                          <>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() =>
                                handleShowFlightLocation(booking, "origin")
                              }
                            >
                              Xem điểm đi trên bản đồ
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() =>
                                handleShowFlightLocation(booking, "destination")
                              }
                            >
                              Xem điểm đến trên bản đồ
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleShowFlightJourney(booking)}
                            >
                              Xem hành trình
                            </Button>
                          </>
                        )}
                        {(booking.bookingType === "HOTEL" ||
                          booking.bookingType === "COMBO") && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleShowHotelLocation(booking)}
                          >
                            Xem khách sạn trên bản đồ
                          </Button>
                        )}
                        {isProcessing && (!hasCountdown || !isExpired) && (
                          <Button
                            size="sm"
                            onClick={() => handleContinueBooking(booking)}
                          >
                            {isAwaitingPayment ||
                            booking.status?.toUpperCase() === "PENDING" ||
                            booking.sagaState?.toUpperCase() ===
                              "PAYMENT_PENDING"
                              ? "Hoàn tất thanh toán"
                              : "Tiếp tục đặt chỗ"}
                          </Button>
                        )}
                        {isCompleted && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleRequestRefund(booking)}
                          >
                            Hoàn tiền
                          </Button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ))}

          {isLoading && (
            <div className="flex items-center justify-center gap-1 text-sm text-gray-300">
              <Loader2 className="h-4 w-4 animate-spin" />
              Đang tải lịch sử đặt chỗ…
            </div>
          )}

          {hasNext && !isLoading && (
            <div className="flex justify-center">
              <Button variant="outline" onClick={handleLoadMore}>
                Tải thêm
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      {refundPaymentData && (
        <RefundDialog
          open={refundDialogOpen}
          onOpenChange={setRefundDialogOpen}
          bookingId={refundPaymentData.bookingId}
          paymentIntentId={refundPaymentData.paymentIntentId}
          transactionId={refundPaymentData.transactionId}
          amount={refundPaymentData.amount}
          currency={refundPaymentData.currency}
          onSuccess={() => {
            setRefundDialogOpen(false);
            // Optionally reload history here
          }}
        />
      )}
    </div>
  );
}
