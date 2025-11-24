"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import {
  Check,
  ChevronsUpDown,
  Calculator,
  Calendar as CalendarIcon,
  X,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import { FlightFareService } from "@/services/flight-fare.service";
import { FlightScheduleService } from "@/services/flight-schedule-service";
import { AdminFormDialog } from "@/components/admin/shared/admin-form-dialog";
import { AirportSelector } from "@/components/admin/flight/airport-selector";
import { Calendar } from "@/components/ui/calendar";
import { format } from "date-fns";
import type {
  FlightFare,
  FlightFareCreateRequest,
  FlightFareUpdateRequest,
  FlightSchedule,
} from "@/types/api";

interface FlightFareFormDialogProps {
  isOpen: boolean;
  onClose: () => void;
  initialData?: FlightFare;
  onSuccess: () => void;
}

export function FlightFareFormDialog({
  isOpen,
  onClose,
  initialData,
  onSuccess,
}: FlightFareFormDialogProps) {
  const [loading, setLoading] = useState(false);
  const [calculating, setCalculating] = useState(false);
  const [scheduleComboOpen, setScheduleComboOpen] = useState(false);

  // Internal schedule management
  const [flightSchedules, setFlightSchedules] = useState<FlightSchedule[]>([]);
  const [loadingSchedules, setLoadingSchedules] = useState(false);

  // Filter states
  const [departureDate, setDepartureDate] = useState<Date | undefined>(
    undefined
  );
  const [departureAirportId, setDepartureAirportId] = useState<number | null>(
    null
  );
  const [arrivalAirportId, setArrivalAirportId] = useState<number | null>(null);
  const [formData, setFormData] = useState<FlightFareCreateRequest>({
    scheduleId: initialData?.scheduleId || "",
    fareClass: initialData?.fareClass || "ECONOMY",
    price: initialData?.price || 0,
    availableSeats: initialData?.availableSeats || 0,
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [calculationParams, setCalculationParams] = useState({
    departureDate: new Date().toISOString().split("T")[0],
    passengerCount: 1,
  });

  // Reset form when dialog opens/closes or initialData changes
  useEffect(() => {
    if (isOpen) {
      setFormData({
        scheduleId: initialData?.scheduleId || "",
        fareClass: initialData?.fareClass || "ECONOMY",
        price: initialData?.price || 0,
        availableSeats: initialData?.availableSeats || 0,
      });
      setErrors({});
      loadFlightSchedules(); // Load schedules when dialog opens
    }
  }, [isOpen, initialData]);

  // Load schedules when filters change
  useEffect(() => {
    if (isOpen) {
      loadFlightSchedules();
    }
  }, [departureDate, departureAirportId, arrivalAirportId]);

  const loadFlightSchedules = async () => {
    try {
      setLoadingSchedules(true);
      const params: any = {
        page: 0,
        size: 100,
        status: "SCHEDULED",
      };

      if (departureDate) {
        params.date = format(departureDate, "yyyy-MM-dd");
      }
      if (departureAirportId) {
        params.departureAirportId = departureAirportId;
      }
      if (arrivalAirportId) {
        params.arrivalAirportId = arrivalAirportId;
      }

      const data = await FlightScheduleService.getFlightSchedules(params);
      setFlightSchedules(data.content);
    } catch (error) {
      console.error("Failed to load flight schedules:", error);
      toast.error("Không thể tải danh sách lịch trình chuyến bay");
    } finally {
      setLoadingSchedules(false);
    }
  };

  // Helper function to get suggested seat count based on schedule and fare class
  const getSuggestedSeatCount = (
    scheduleId: string,
    fareClass: string
  ): number => {
    const schedule = flightSchedules.find((s) => s.scheduleId === scheduleId);
    if (!schedule?.aircraft) return 0;

    const aircraft = schedule.aircraft;
    switch (fareClass) {
      case "FIRST":
        return aircraft.capacityFirst || 0;
      case "BUSINESS":
        return aircraft.capacityBusiness || 0;
      case "PREMIUM_ECONOMY":
        return Math.floor((aircraft.capacityEconomy || 0) * 0.3); // 30% of economy
      case "ECONOMY":
        return aircraft.capacityEconomy || 0;
      default:
        return 0;
    }
  };

  // Auto-populate seat count when schedule or fare class changes
  useEffect(() => {
    if (formData.scheduleId && formData.fareClass) {
      const suggestedSeats = getSuggestedSeatCount(
        formData.scheduleId,
        formData.fareClass
      );
      if (suggestedSeats > 0) {
        setFormData((prev) => ({ ...prev, availableSeats: suggestedSeats }));
      }
    }
  }, [formData.scheduleId, formData.fareClass]);

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formData.scheduleId) {
      newErrors.scheduleId = "Flight schedule is required";
    }

    if (!formData.fareClass) {
      newErrors.fareClass = "Fare class is required";
    }

    if (!formData.price || formData.price <= 0) {
      newErrors.price = "Price must be greater than 0";
    }

    if (!formData.availableSeats || formData.availableSeats <= 0) {
      newErrors.availableSeats = "Available seats must be greater than 0";
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    setLoading(true);

    try {
      if (initialData) {
        // Update existing fare
        const updateData: FlightFareUpdateRequest = {
          fareClass: formData.fareClass,
          price: formData.price,
          availableSeats: formData.availableSeats,
        };
        await FlightFareService.updateFlightFare(
          initialData.fareId,
          updateData
        );
        toast.success("Flight fare updated successfully");
      } else {
        // Create new fare
        const createData: FlightFareCreateRequest = formData;
        await FlightFareService.createFlightFare(createData);
        toast.success("Flight fare created successfully");
      }

      onSuccess();
    } catch (error: any) {
      console.error("Error saving flight fare:", error);

      if (error.response?.data?.message) {
        toast.error(error.response.data.message);
      } else {
        toast.error(
          initialData
            ? "Failed to update flight fare"
            : "Failed to create flight fare"
        );
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCalculatePrice = async () => {
    if (!formData.scheduleId || !formData.fareClass) {
      toast.error("Please select a flight schedule and fare class first");
      return;
    }

    setCalculating(true);
    try {
      const result = await FlightFareService.calculateFlightFares({
        scheduleIds: [formData.scheduleId],
        fareClass: formData.fareClass,
        departureDate: calculationParams.departureDate,
        passengerCount: calculationParams.passengerCount,
      });

      if (result && result.length > 0) {
        const calculatedFare = result[0];
        setFormData((prev) => ({
          ...prev,
          price: calculatedFare.calculatedPrice,
        }));
        toast.success(
          `Price calculated: ${formatPrice(calculatedFare.calculatedPrice)}`
        );
      } else {
        toast.error("Could not calculate price for the selected flight");
      }
    } catch (error: any) {
      console.error("Error calculating flight fare:", error);
      toast.error("Failed to calculate price");
    } finally {
      setCalculating(false);
    }
  };

  const selectedSchedule = flightSchedules.find(
    (s) => s.scheduleId === formData.scheduleId
  );

  const getFareClassBadge = (fareClass: string) => {
    switch (fareClass) {
      case "ECONOMY":
        return <Badge className="bg-blue-100 text-blue-800">Phổ thông</Badge>;
      case "PREMIUM_ECONOMY":
        return (
          <Badge className="bg-purple-100 text-purple-800">
            Phổ thông đặc biệt
          </Badge>
        );
      case "BUSINESS":
        return (
          <Badge className="bg-green-100 text-green-800">Thương gia</Badge>
        );
      case "FIRST":
        return (
          <Badge className="bg-yellow-100 text-yellow-800">Hạng nhất</Badge>
        );
      default:
        return <Badge>{fareClass}</Badge>;
    }
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: "VND",
    }).format(price);
  };

  // Custom fields for the AdminFormDialog
  const customFields = [
    {
      name: "scheduleFilters",
      label: "Lọc lịch trình",
      component: (
        <div className="space-y-4 border rounded-lg p-4 bg-muted/50">
          <div className="flex items-center justify-between">
            <Label className="text-sm font-medium">
              Bộ lọc lịch trình chuyến bay
            </Label>
            {(departureDate || departureAirportId || arrivalAirportId) && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => {
                  setDepartureDate(undefined);
                  setDepartureAirportId(null);
                  setArrivalAirportId(null);
                }}
                className="h-7 px-2"
              >
                <X className="h-3 w-3 mr-1" />
                Xóa
              </Button>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {/* Date Filter */}
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">
                Ngày khởi hành
              </Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button
                    type="button"
                    variant="outline"
                    className={cn(
                      "w-full justify-start text-left font-normal",
                      !departureDate && "text-muted-foreground"
                    )}
                  >
                    <CalendarIcon className="mr-2 h-4 w-4" />
                    {departureDate
                      ? format(departureDate, "dd/MM/yyyy")
                      : "Chọn ngày"}
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0">
                  <Calendar
                    mode="single"
                    selected={departureDate}
                    onSelect={setDepartureDate}
                    initialFocus
                  />
                </PopoverContent>
              </Popover>
            </div>

            {/* Departure Airport */}
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">
                Sân bay đi
              </Label>
              <AirportSelector
                value={departureAirportId}
                onChange={setDepartureAirportId}
                placeholder="Chọn sân bay đi"
              />
            </div>

            {/* Arrival Airport */}
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">
                Sân bay đến
              </Label>
              <AirportSelector
                value={arrivalAirportId}
                onChange={setArrivalAirportId}
                placeholder="Chọn sân bay đến"
              />
            </div>
          </div>

          {loadingSchedules && (
            <p className="text-xs text-muted-foreground">
              Đang tải lịch trình...
            </p>
          )}
        </div>
      ),
    },
    {
      name: "scheduleSelection",
      label: "Flight Schedule *",
      component: (
        <div className="space-y-2">
          <Popover open={scheduleComboOpen} onOpenChange={setScheduleComboOpen}>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                role="combobox"
                aria-expanded={scheduleComboOpen}
                className="w-full justify-between"
              >
                {formData.scheduleId
                  ? (() => {
                      const schedule = flightSchedules.find(
                        (s) => s.scheduleId === formData.scheduleId
                      );
                      return schedule ? (
                        <div className="flex flex-col text-left">
                          <span className="font-medium">
                            {schedule.flight?.flightNumber ||
                              `Flight ${schedule.flightId}`}
                          </span>
                          <span className="text-sm text-muted-foreground">
                            {schedule.flight ? (
                              <>
                                <span>
                                  {schedule.flight.airlineName || "N/A"}
                                </span>
                                <span className="mx-1">•</span>
                                <span>
                                  {schedule.flight.departureAirport?.iataCode} →{" "}
                                  {schedule.flight.arrivalAirport?.iataCode}
                                </span>
                              </>
                            ) : (
                              `Schedule ${schedule.scheduleId.slice(0, 8)}...`
                            )}
                          </span>
                        </div>
                      ) : (
                        "Select a flight schedule"
                      );
                    })()
                  : "Select a flight schedule"}
                <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-full p-0">
              <Command>
                <CommandInput placeholder="Search schedules..." />
                <CommandEmpty>No schedule found.</CommandEmpty>
                <CommandGroup className="max-h-[200px] overflow-y-auto">
                  {flightSchedules.map((schedule) => (
                    <CommandItem
                      key={schedule.scheduleId}
                      value={`${schedule.flight?.flightNumber} ${schedule.flight?.airlineName} ${schedule.flight?.departureAirport?.iataCode} ${schedule.flight?.arrivalAirport?.iataCode} ${schedule.scheduleId}`}
                      onSelect={() => {
                        setFormData({
                          ...formData,
                          scheduleId: schedule.scheduleId,
                        });
                        setScheduleComboOpen(false);
                      }}
                    >
                      <Check
                        className={cn(
                          "mr-2 h-4 w-4",
                          formData.scheduleId === schedule.scheduleId
                            ? "opacity-100"
                            : "opacity-0"
                        )}
                      />
                      <div className="flex flex-col text-left">
                        <span className="font-medium">
                          {schedule.flight?.flightNumber ||
                            `Flight ${schedule.flightId}`}
                        </span>
                        <span className="text-sm text-muted-foreground">
                          {schedule.flight ? (
                            <>
                              <span>
                                {schedule.flight.airlineName ||
                                  "Unknown Airline"}
                              </span>
                              <span className="mx-1">•</span>
                              <span>
                                {schedule.flight.departureAirport?.iataCode ||
                                  "N/A"}{" "}
                                →{" "}
                                {schedule.flight.arrivalAirport?.iataCode ||
                                  "N/A"}
                              </span>
                              <span className="mx-1">•</span>
                              <span>
                                {FlightScheduleService.formatScheduleTime(
                                  schedule.departureTime
                                )}
                              </span>
                            </>
                          ) : (
                            `Schedule ${schedule.scheduleId.slice(0, 8)}...`
                          )}
                        </span>
                        {schedule.durationMinutes && (
                          <span className="text-xs text-muted-foreground">
                            Duration:{" "}
                            {FlightScheduleService.formatDuration(
                              schedule.durationMinutes
                            )}
                          </span>
                        )}
                      </div>
                    </CommandItem>
                  ))}
                </CommandGroup>
              </Command>
            </PopoverContent>
          </Popover>
          {errors.scheduleId && (
            <p className="text-sm text-red-500">{errors.scheduleId}</p>
          )}
        </div>
      ),
      error: errors.scheduleId,
    },
    {
      name: "scheduleDetails",
      label: "",
      component: selectedSchedule ? (
        <Card>
          <CardContent className="pt-4">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-medium text-sm">Selected Schedule</span>
                <Badge variant="secondary">{selectedSchedule.status}</Badge>
              </div>
              {selectedSchedule.flight && (
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <span className="text-muted-foreground">Flight:</span>
                    <span className="ml-1 font-medium">
                      {selectedSchedule.flight.flightNumber}
                    </span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Airline:</span>
                    <span className="ml-1">
                      {selectedSchedule.flight.airlineName || "N/A"}
                    </span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Route:</span>
                    <span className="ml-1">
                      {selectedSchedule.flight.departureAirport?.iataCode ||
                        "N/A"}{" "}
                      →{" "}
                      {selectedSchedule.flight.arrivalAirport?.iataCode ||
                        "N/A"}
                    </span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Duration:</span>
                    <span className="ml-1">
                      {selectedSchedule.durationMinutes
                        ? FlightScheduleService.formatDuration(
                            selectedSchedule.durationMinutes
                          )
                        : "N/A"}
                    </span>
                  </div>
                </div>
              )}
              {selectedSchedule.aircraft && (
                <div className="text-xs text-muted-foreground pt-1 border-t">
                  <span>Aircraft: {selectedSchedule.aircraft.model}</span>
                  <span className="mx-2">•</span>
                  <span>
                    Capacity: {selectedSchedule.aircraft.totalCapacity} seats
                  </span>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      ) : null,
    },
    {
      name: "fareClassSelection",
      label: "Fare Class *",
      component: (
        <div className="space-y-2">
          <Select
            value={formData.fareClass}
            onValueChange={(value: any) =>
              setFormData({ ...formData, fareClass: value })
            }
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ECONOMY">Phổ thông</SelectItem>
              <SelectItem value="PREMIUM_ECONOMY">
                Phổ thông đặc biệt
              </SelectItem>
              <SelectItem value="BUSINESS">Thương gia</SelectItem>
              <SelectItem value="FIRST">Hạng nhất</SelectItem>
            </SelectContent>
          </Select>
          {formData.fareClass && (
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Selected:</span>
              {getFareClassBadge(formData.fareClass)}
            </div>
          )}
          {errors.fareClass && (
            <p className="text-sm text-red-500">{errors.fareClass}</p>
          )}
        </div>
      ),
      error: errors.fareClass,
    },
    {
      name: "priceCalculation",
      label: "Price Calculation",
      component: (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label className="text-sm font-medium">Departure Date</Label>
              <div className="relative">
                <Input
                  type="date"
                  value={calculationParams.departureDate}
                  onChange={(e) =>
                    setCalculationParams((prev) => ({
                      ...prev,
                      departureDate: e.target.value,
                    }))
                  }
                  className="pl-10"
                />
                <Calendar className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              </div>
            </div>
            <div className="space-y-2">
              <Label className="text-sm font-medium">Passengers</Label>
              <Input
                type="number"
                min="1"
                max="100"
                value={calculationParams.passengerCount}
                onChange={(e) =>
                  setCalculationParams((prev) => ({
                    ...prev,
                    passengerCount: parseInt(e.target.value) || 1,
                  }))
                }
              />
            </div>
          </div>
          <Button
            type="button"
            variant="outline"
            onClick={handleCalculatePrice}
            disabled={
              calculating || !formData.scheduleId || !formData.fareClass
            }
            className="w-full"
          >
            <Calculator className="mr-2 h-4 w-4" />
            {calculating ? "Calculating..." : "Calculate Suggested Price"}
          </Button>
          <Separator />
        </div>
      ),
    },
    {
      name: "priceInput",
      label: "Price (VND) *",
      component: (
        <div className="space-y-2">
          <Input
            type="number"
            value={formData.price}
            onChange={(e) =>
              setFormData({ ...formData, price: Number(e.target.value) })
            }
            placeholder="Enter price"
            min="0"
            required
          />
          {formData.price > 0 && (
            <div className="text-sm text-muted-foreground">
              Formatted: {formatPrice(formData.price)}
            </div>
          )}
          {errors.price && (
            <p className="text-sm text-red-500">{errors.price}</p>
          )}
        </div>
      ),
      error: errors.price,
    },
    {
      name: "seatsInput",
      label: "Available Seats *",
      component: (
        <div className="space-y-2">
          <Input
            type="number"
            value={formData.availableSeats}
            onChange={(e) =>
              setFormData({
                ...formData,
                availableSeats: Number(e.target.value),
              })
            }
            placeholder="Enter available seats"
            min="0"
            required
          />
          {formData.scheduleId && formData.fareClass && (
            <div className="text-xs text-muted-foreground">
              Suggested capacity for {formData.fareClass.toLowerCase()}:{" "}
              {getSuggestedSeatCount(formData.scheduleId, formData.fareClass)}{" "}
              seats
            </div>
          )}
          {errors.availableSeats && (
            <p className="text-sm text-red-500">{errors.availableSeats}</p>
          )}
        </div>
      ),
      error: errors.availableSeats,
    },
  ];

  return (
    <AdminFormDialog
      isOpen={isOpen}
      onClose={onClose}
      title={initialData ? "Edit Flight Fare" : "Create Flight Fare"}
      description={
        initialData
          ? "Update flight fare information"
          : "Create a new flight fare"
      }
      customFields={customFields}
      onSubmit={handleSubmit}
      submitLabel={initialData ? "Update Fare" : "Create Fare"}
      isSubmitting={loading}
      canSubmit={
        Object.keys(errors).length === 0 &&
        !!formData.scheduleId &&
        !!formData.fareClass &&
        formData.price > 0 &&
        formData.availableSeats > 0
      }
    />
  );
}
