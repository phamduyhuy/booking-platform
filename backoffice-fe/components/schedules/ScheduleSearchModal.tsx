"use client";

import { useState, useEffect } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Calendar } from "@/components/ui/calendar";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { FlightScheduleService } from "@/services/flight-schedule-service";
import { format } from "date-fns";
import { Search, CalendarIcon, Plane, Loader2 } from "lucide-react";

interface FlightSchedule {
  scheduleId: string;
  flightId: number;
  departureTime: string;
  arrivalTime: string;
  status: string;
  flight?: {
    flightNumber: string;
    departureAirport?: {
      name: string;
      iataCode: string;
      city: string;
    };
    arrivalAirport?: {
      name: string;
      iataCode: string;
      city: string;
    };
  };
}

interface ScheduleSearchModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelectSchedule: (schedule: FlightSchedule) => void;
}

export default function ScheduleSearchModal({
  open,
  onOpenChange,
  onSelectSchedule,
}: ScheduleSearchModalProps) {
  const [selectedDate, setSelectedDate] = useState<Date | undefined>(undefined);
  const [searchTerm, setSearchTerm] = useState("");
  const [schedules, setSchedules] = useState<FlightSchedule[]>([]);
  const [loading, setLoading] = useState(false);
  const [debouncedSearch, setDebouncedSearch] = useState("");

  // Debounce search term
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm);
    }, 300);

    return () => clearTimeout(timer);
  }, [searchTerm]);

  // Load schedules when filters change
  useEffect(() => {
    if (!open) return;

    const loadSchedules = async () => {
      try {
        setLoading(true);
        const params: any = {
          page: 0,
          size: 20,
        };

        if (debouncedSearch) {
          params.search = debouncedSearch;
        }

        if (selectedDate) {
          params.date = format(selectedDate, "yyyy-MM-dd");
        }

        const response = await FlightScheduleService.getFlightSchedules(params);
        setSchedules(response.content || []);
      } catch (error) {
        console.error("Failed to load schedules:", error);
        setSchedules([]);
      } finally {
        setLoading(false);
      }
    };

    loadSchedules();
  }, [debouncedSearch, selectedDate, open]);

  const handleSelectSchedule = (schedule: FlightSchedule) => {
    onSelectSchedule(schedule);
    onOpenChange(false);
  };

  const handleClearFilters = () => {
    setSelectedDate(undefined);
    setSearchTerm("");
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-5xl max-h-[85vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle>Chọn lịch trình chuyến bay</DialogTitle>
        </DialogHeader>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 flex-1 overflow-hidden">
          {/* Left side: Date picker */}
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <CalendarIcon className="h-4 w-4 text-primary" />
              <h3 className="font-medium">Chọn ngày khởi hành</h3>
            </div>
            <div className="flex justify-center">
              <Calendar
                mode="single"
                selected={selectedDate}
                onSelect={setSelectedDate}
                className="rounded-md border shadow-sm"
              />
            </div>
            {selectedDate && (
              <div className="text-center">
                <p className="text-sm font-medium text-primary">
                  Đã chọn: {format(selectedDate, "dd/MM/yyyy")}
                </p>
              </div>
            )}
          </div>

          {/* Right side: Search and results */}
          <div className="space-y-4 flex flex-col overflow-hidden">
            <div className="flex items-center gap-2">
              <Search className="h-4 w-4 text-primary" />
              <h3 className="font-medium">Tìm kiếm chuyến bay</h3>
            </div>

            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Tìm theo mã chuyến bay, sân bay..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>

            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={handleClearFilters}
                disabled={!selectedDate && !searchTerm}
              >
                Xóa bộ lọc
              </Button>
              {loading && (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>Đang tải...</span>
                </div>
              )}
            </div>

            {/* Results */}
            <div className="flex-1 overflow-y-auto border rounded-lg">
              <div className="p-2 space-y-2">
                {!loading && schedules.length === 0 && (
                  <div className="text-center py-12 text-muted-foreground">
                    <Plane className="h-12 w-12 mx-auto mb-3 opacity-20" />
                    <p className="font-medium">Không tìm thấy lịch trình</p>
                    <p className="text-sm mt-1">Thử điều chỉnh bộ lọc của bạn</p>
                  </div>
                )}

                {schedules.map((schedule) => (
                  <button
                    key={schedule.scheduleId}
                    onClick={() => handleSelectSchedule(schedule)}
                    className="w-full text-left p-4 rounded-lg border hover:border-primary hover:bg-accent transition-all group"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="space-y-2 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <Plane className="h-4 w-4 text-primary" />
                          <span className="font-semibold text-base">
                            {schedule.flight?.flightNumber || "N/A"}
                          </span>
                          <span className="text-xs px-2 py-1 rounded-full bg-primary/10 text-primary font-medium">
                            {schedule.status}
                          </span>
                        </div>

                        <div className="space-y-1">
                          <div className="flex items-center gap-2 text-sm">
                            <span className="font-medium text-foreground">
                              {schedule.flight?.departureAirport?.iataCode || "?"}
                            </span>
                            <span className="text-muted-foreground">→</span>
                            <span className="font-medium text-foreground">
                              {schedule.flight?.arrivalAirport?.iataCode || "?"}
                            </span>
                          </div>
                          <div className="text-sm text-muted-foreground">
                            {schedule.flight?.departureAirport?.city} →{" "}
                            {schedule.flight?.arrivalAirport?.city}
                          </div>
                        </div>

                        <div className="text-xs text-muted-foreground">
                          {new Date(schedule.departureTime).toLocaleString("vi-VN", {
                            dateStyle: "medium",
                            timeStyle: "short",
                          })}
                        </div>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
