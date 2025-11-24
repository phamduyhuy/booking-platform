"use client";

import { useState, useEffect, useCallback } from "react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  MoreHorizontal,
  Eye,
  Edit,
  Plus,
  Trash2,
  DollarSign,
  Plane,
  X,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { AdminLayout } from "@/components/admin/admin-layout";
import { FlightFareService } from "@/services/flight-fare.service";
import { FlightFareFormDialog } from "@/components/admin/flight/fare-form-dialog";
import ScheduleSearchModal from "@/components/schedules/ScheduleSearchModal";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import { format } from "date-fns";
import type {
  FlightFare,
  FlightSchedule,
  PaginatedResponse,
  FlightFareCreateRequest,
  FlightFareUpdateRequest,
} from "@/types/api";

export default function AdminFlightFares() {
  const [flightFares, setFlightFares] =
    useState<PaginatedResponse<FlightFare> | null>(null);
  const [selectedSchedule, setSelectedSchedule] = useState<FlightSchedule | null>(null);
  const [statistics, setStatistics] = useState<{
    totalFares: number;
    economyFares: number;
    businessFares: number;
    firstFares: number;
    premiumEconomyFares: number;
  } | null>(null);
  const [loading, setLoading] = useState(true);
  const [fareClassFilter, setFareClassFilter] = useState<string | undefined>(undefined);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedFare, setSelectedFare] = useState<FlightFare | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [scheduleModalOpen, setScheduleModalOpen] = useState(false);

  // Form states for legacy compatibility
  const [formData, setFormData] = useState<FlightFareCreateRequest>({
    scheduleId: "",
    fareClass: "ECONOMY",
    price: 0,
    availableSeats: 0,
  });
  const [editFormData, setEditFormData] = useState<FlightFareUpdateRequest>({});

  const { toast } = useToast();

  // Helper function to get suggested seat count based on schedule and fare class
  const getSuggestedSeatCount = (
    schedule: FlightSchedule | null,
    fareClass: string
  ): number => {
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

  const loadStatistics = async () => {
    try {
      const stats = await FlightFareService.getFlightFareStatistics();
      setStatistics({
        totalFares: (stats as any).totalFares || 0,
        economyFares: (stats as any).economyFares || 0,
        businessFares: (stats as any).businessFares || 0,
        firstFares: (stats as any).firstFares || 0,
        premiumEconomyFares: (stats as any).premiumEconomyFares || 0,
      });
    } catch (error) {
      console.error("Failed to load statistics:", error);
    }
  };

  const handleSelectSchedule = (schedule: FlightSchedule) => {
    setSelectedSchedule(schedule);
    setCurrentPage(0);
  };

  const handleClearScheduleFilter = () => {
    setSelectedSchedule(null);
    setCurrentPage(0);
  };

  // Load flight fares when filters change
  useEffect(() => {
    loadFlightFares();
    loadStatistics();
  }, [selectedSchedule, fareClassFilter, currentPage]);

  const loadFlightFares = async () => {
    try {
      setLoading(true);
      const data = await FlightFareService.getFlightFares({
        scheduleId: selectedSchedule?.scheduleId || undefined,
        fareClass: fareClassFilter || undefined,
        page: currentPage,
        size: 10,
      });
      setFlightFares(data);
    } catch (error) {
      console.error("Failed to load flight fares:", error);
      toast({
        title: "Lỗi",
        description: "Không thể tải danh sách giá vé",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleEditFare = (fare: FlightFare) => {
    setSelectedFare(fare);
    setEditFormData({
      fareClass: fare.fareClass,
      price: fare.price,
      availableSeats: fare.availableSeats,
    });
    setEditDialogOpen(true);
  };

  const handleDeleteFare = (fare: FlightFare) => {
    setSelectedFare(fare);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!selectedFare) return;

    try {
      await FlightFareService.deleteFlightFare(selectedFare.fareId);
      toast({
        title: "Thành công",
        description: "Đã xóa giá vé thành công",
      });
      setDeleteDialogOpen(false);
      setSelectedFare(null);
      handleFormSuccess();
    } catch (error) {
      toast({
        title: "Lỗi",
        description: "Không thể xóa giá vé",
        variant: "destructive",
      });
    }
  };

  const resetForm = () => {
    setFormData({
      scheduleId: "",
      fareClass: "ECONOMY",
      price: 0,
      availableSeats: 0,
    });
  };

  const handleFormSuccess = () => {
    loadFlightFares();
    loadStatistics();
  };

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

  return (
    <AdminLayout>
      <div className="space-y-8">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">
            Quản lý giá vé máy bay
          </h2>
          <p className="text-muted-foreground">
            Quản lý giá vé cho các chuyến bay theo hạng ghế
          </p>
        </div>

        {/* Statistics Cards */}
        <div className="grid gap-4 md:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Tổng giá vé</CardTitle>
              <DollarSign className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {statistics?.totalFares || 0}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Phổ thông</CardTitle>
              <Plane className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {statistics?.economyFares || 0}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Thương gia</CardTitle>
              <Plane className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {statistics?.businessFares || 0}
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Hạng nhất</CardTitle>
              <Plane className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {statistics?.firstFares || 0}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Filters and Actions */}
        <Card>
          <CardHeader>
            <CardTitle>Danh sách giá vé</CardTitle>
            <CardDescription>Quản lý giá vé cho các chuyến bay</CardDescription>
          </CardHeader>
          <CardContent>
            {/* Simplified Filters */}
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                {/* Schedule Filter Button */}
                <Button
                  variant="outline"
                  onClick={() => setScheduleModalOpen(true)}
                >
                  <Plane className="mr-2 h-4 w-4" />
                  {selectedSchedule ? "Thay đổi lịch trình" : "Chọn lịch trình"}
                </Button>

                {/* Show selected schedule info */}
                {selectedSchedule && (
                  <div className="flex items-center gap-2 px-3 py-2 rounded-md border bg-muted/50">
                    <div className="text-sm">
                      <span className="font-medium">
                        {selectedSchedule.flight?.flightNumber}
                      </span>
                      <span className="text-muted-foreground ml-2">
                        {selectedSchedule.flight?.departureAirport?.iataCode} →{" "}
                        {selectedSchedule.flight?.arrivalAirport?.iataCode}
                      </span>
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={handleClearScheduleFilter}
                      className="h-6 w-6 p-0"
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                )}

                {/* Fare Class Filter */}
                <Select 
                  value={fareClassFilter || "all"} 
                  onValueChange={(value) => setFareClassFilter(value === "all" ? undefined : value)}
                >
                  <SelectTrigger className="w-[200px]">
                    <SelectValue placeholder="Chọn hạng ghế" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">Tất cả hạng ghế</SelectItem>
                    <SelectItem value="ECONOMY">Phổ thông</SelectItem>
                    <SelectItem value="PREMIUM_ECONOMY">Phổ thông đặc biệt</SelectItem>
                    <SelectItem value="BUSINESS">Thương gia</SelectItem>
                    <SelectItem value="FIRST">Hạng nhất</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <Button
                onClick={() => setCreateDialogOpen(true)}
                className="bg-blue-600 hover:bg-blue-700"
              >
                <Plus className="mr-2 h-4 w-4" />
                Thêm giá vé
              </Button>
            </div>

            {/* Flight Fares Table */}
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Mã giá vé</TableHead>
                    <TableHead>Chuyến bay</TableHead>
                    <TableHead>Lịch trình</TableHead>
                    <TableHead>Hạng ghế</TableHead>
                    <TableHead>Giá vé</TableHead>
                    <TableHead>Ghế trống</TableHead>
                    <TableHead></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {loading ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-center">
                        Đang tải...
                      </TableCell>
                    </TableRow>
                  ) : flightFares?.content?.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-center">
                        Không có dữ liệu
                      </TableCell>
                    </TableRow>
                  ) : (
                    flightFares?.content?.map((fare) => {
                      // Schedule data should be included in fare response
                      const schedule = fare.schedule;
                      return (
                        <TableRow key={fare.fareId}>
                          <TableCell className="font-medium">
                            {fare.fareId.slice(0, 8)}...
                          </TableCell>
                          <TableCell>
                            {schedule?.flight ? (
                              <div className="flex flex-col">
                                <span className="font-medium">
                                  {schedule.flight.flightNumber}
                                </span>
                                <span className="text-sm text-muted-foreground">
                                  {schedule.flight.airlineName || "N/A"}
                                </span>
                              </div>
                            ) : (
                              <span className="text-muted-foreground">-</span>
                            )}
                          </TableCell>
                          <TableCell>
                            {schedule ? (
                              <div className="flex flex-col">
                                <span className="text-sm">
                                  {schedule.flight
                                    ? `${schedule.flight.departureAirport?.iataCode} → ${schedule.flight.arrivalAirport?.iataCode}`
                                    : `Schedule ${schedule.scheduleId.slice(
                                        0,
                                        8
                                      )}...`}
                                </span>
                                <span className="text-xs text-muted-foreground">
                                  {schedule.departureTime && new Date(schedule.departureTime).toLocaleString('vi-VN', {
                                    dateStyle: 'short',
                                    timeStyle: 'short'
                                  })}
                                </span>
                              </div>
                            ) : (
                              <span className="text-muted-foreground">
                                {fare.scheduleId.slice(0, 8)}...
                              </span>
                            )}
                          </TableCell>
                          <TableCell>
                            {getFareClassBadge(fare.fareClass)}
                          </TableCell>
                          <TableCell className="font-semibold text-green-600">
                            {formatPrice(fare.price)}
                          </TableCell>
                          <TableCell>{fare.availableSeats}</TableCell>
                          <TableCell>
                            <DropdownMenu>
                              <DropdownMenuTrigger asChild>
                                <Button variant="ghost" className="h-8 w-8 p-0">
                                  <MoreHorizontal className="h-4 w-4" />
                                </Button>
                              </DropdownMenuTrigger>
                              <DropdownMenuContent align="end">
                                <DropdownMenuItem
                                  onClick={() => handleEditFare(fare)}
                                >
                                  <Edit className="mr-2 h-4 w-4" />
                                  Chỉnh sửa
                                </DropdownMenuItem>
                                <DropdownMenuItem
                                  onClick={() => handleDeleteFare(fare)}
                                  className="text-red-600"
                                >
                                  <Trash2 className="mr-2 h-4 w-4" />
                                  Xóa
                                </DropdownMenuItem>
                              </DropdownMenuContent>
                            </DropdownMenu>
                          </TableCell>
                        </TableRow>
                      );
                    })
                  )}
                </TableBody>
              </Table>
            </div>

            {/* Pagination */}
            {flightFares && flightFares.totalPages > 1 && (
              <div className="flex items-center justify-between space-x-2 py-4">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
                  disabled={currentPage === 0}
                >
                  Trước
                </Button>
                <div className="text-sm text-muted-foreground">
                  Trang {currentPage + 1} / {flightFares.totalPages}
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setCurrentPage(
                      Math.min(flightFares.totalPages - 1, currentPage + 1)
                    )
                  }
                  disabled={currentPage >= flightFares.totalPages - 1}
                >
                  Sau
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Create Flight Fare Dialog */}
      <FlightFareFormDialog
        isOpen={createDialogOpen}
        onClose={() => {
          setCreateDialogOpen(false);
          resetForm();
        }}
        onSuccess={() => {
          setCreateDialogOpen(false);
          resetForm();
          handleFormSuccess();
        }}
      />

      {/* Edit Flight Fare Dialog */}
      <FlightFareFormDialog
        isOpen={editDialogOpen}
        onClose={() => {
          setEditDialogOpen(false);
          setSelectedFare(null);
        }}
        initialData={selectedFare || undefined}
        onSuccess={() => {
          setEditDialogOpen(false);
          setSelectedFare(null);
          handleFormSuccess();
        }}
      />

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn có chắc chắn muốn xóa giá vé này? Hành động này không thể hoàn
              tác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmDelete}
              className="bg-red-600 hover:bg-red-700"
            >
              Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Schedule Search Modal */}
      <ScheduleSearchModal
        open={scheduleModalOpen}
        onOpenChange={setScheduleModalOpen}
        onSelectSchedule={handleSelectSchedule}
      />
    </AdminLayout>
  );
}
