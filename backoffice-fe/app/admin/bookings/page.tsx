"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import {
  Search,
  MoreHorizontal,
  Eye,
  Edit,
  X,
  Calendar,
  DollarSign,
  Users,
  AlertTriangle,
  RefreshCw,
} from "lucide-react";
import { AdminLayout } from "@/components/admin/admin-layout";
import { BookingDetailDialog } from "@/components/admin/booking-detail-dialog";
import { BookingService } from "@/services/booking-service";
import type { Booking, PaginatedResponse } from "@/types/api";
import { toast } from "@/hooks/use-toast";

export default function AdminBookings() {
  const [bookings, setBookings] = useState<PaginatedResponse<Booking> | null>(
    null
  );
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [typeFilter, setTypeFilter] = useState<string>("all");
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(20);
  const [stats, setStats] = useState({
    totalBookings: 0,
    confirmedBookings: 0,
    pendingBookings: 0,
    totalRevenue: 0,
  });

  // Dialog states
  const [selectedBooking, setSelectedBooking] = useState<Booking | null>(null);
  const [showDetailDialog, setShowDetailDialog] = useState(false);
  const [showStatusDialog, setShowStatusDialog] = useState(false);
  const [showCancelDialog, setShowCancelDialog] = useState(false);
  const [newStatus, setNewStatus] = useState<string>("");
  const [reason, setReason] = useState("");
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    setCurrentPage(1); // Reset to first page when filters change
  }, [searchTerm, statusFilter, typeFilter]);

  useEffect(() => {
    loadBookings();
    loadStats();
  }, [currentPage, searchTerm, statusFilter, typeFilter]);

  const loadStats = async () => {
    try {
      const summary = await BookingService.getBookingSummary();
      setStats({
        totalBookings: summary.totalBookings,
        confirmedBookings: summary.confirmedBookings,
        pendingBookings: summary.pendingBookings,
        totalRevenue: summary.totalRevenue,
      });
    } catch (error) {
      console.error("Failed to load booking stats:", error);
    }
  };

  const loadBookings = async () => {
    try {
      setLoading(true);
      const data = await BookingService.getBookings({
        search: searchTerm || undefined,
        status: statusFilter === "all" ? undefined : statusFilter,
        type: typeFilter === "all" ? undefined : typeFilter,
        page: currentPage,
        size: pageSize,
      });
      setBookings(data);
    } catch (error) {
      console.error("Failed to load bookings:", error);
      toast({
        title: "Error",
        description: "Failed to load bookings. Please try again.",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleStatusUpdate = async () => {
    if (!selectedBooking || !newStatus) return;

    try {
      setActionLoading(true);
      await BookingService.updateBookingStatus(
        selectedBooking.bookingId,
        newStatus as Booking["status"],
        reason
      );

      toast({
        title: "Success",
        description: "Booking status updated successfully",
      });

      setShowStatusDialog(false);
      setReason("");
      setNewStatus("");
      setSelectedBooking(null);
      loadBookings(); // Refresh the list
      loadStats(); // Refresh stats
    } catch (error) {
      console.error("Failed to update booking status:", error);
      toast({
        title: "Error",
        description: "Failed to update booking status. Please try again.",
        variant: "destructive",
      });
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancelBooking = async () => {
    if (!selectedBooking) return;

    try {
      setActionLoading(true);
      await BookingService.cancelBooking(selectedBooking.bookingId, reason);

      toast({
        title: "Success",
        description: "Booking cancelled successfully",
      });

      setShowCancelDialog(false);
      setReason("");
      setSelectedBooking(null);
      loadBookings(); // Refresh the list
      loadStats(); // Refresh stats
    } catch (error) {
      console.error("Failed to cancel booking:", error);
      toast({
        title: "Error",
        description: "Failed to cancel booking. Please try again.",
        variant: "destructive",
      });
    } finally {
      setActionLoading(false);
    }
  };

  const openDetailDialog = (booking: Booking) => {
    setSelectedBooking(booking);
    setShowDetailDialog(true);
  };

  const openStatusDialog = (booking: Booking) => {
    setSelectedBooking(booking);
    setNewStatus(booking.status);
    setShowStatusDialog(true);
  };

  const openCancelDialog = (booking: Booking) => {
    setSelectedBooking(booking);
    setShowCancelDialog(true);
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "CONFIRMED":
        return (
          <Badge className="bg-green-100 text-green-800">Đã xác nhận</Badge>
        );
      case "PENDING":
        return (
          <Badge className="bg-yellow-100 text-yellow-800">Chờ xử lý</Badge>
        );
      case "VALIDATION_PENDING":
        return (
          <Badge className="bg-orange-100 text-orange-800">Chờ xác thực</Badge>
        );
      case "PAYMENT_PENDING":
        return (
          <Badge className="bg-blue-100 text-blue-800">Chờ thanh toán</Badge>
        );
      case "PAID":
        return (
          <Badge className="bg-green-100 text-green-800">Đã thanh toán</Badge>
        );
      case "PAYMENT_FAILED":
        return (
          <Badge className="bg-red-100 text-red-800">Thanh toán thất bại</Badge>
        );
      case "CANCELLED":
        return <Badge className="bg-red-100 text-red-800">Đã hủy</Badge>;
      case "FAILED":
        return <Badge className="bg-red-100 text-red-800">Thất bại</Badge>;
      case "VALIDATION_FAILED":
        return (
          <Badge className="bg-red-100 text-red-800">Xác thực thất bại</Badge>
        );
      default:
        return <Badge variant="secondary">{status}</Badge>;
    }
  };

  const getTypeBadge = (bookingType: string) => {
    switch (bookingType) {
      case "FLIGHT":
        return (
          <Badge variant="outline" className="text-blue-600 border-blue-200">
            Chuyến bay
          </Badge>
        );
      case "HOTEL":
        return (
          <Badge variant="outline" className="text-green-600 border-green-200">
            Khách sạn
          </Badge>
        );
      case "COMBO":
        return (
          <Badge
            variant="outline"
            className="text-purple-600 border-purple-200"
          >
            Combo
          </Badge>
        );
      default:
        return <Badge variant="outline">{bookingType}</Badge>;
    }
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: "VND",
    }).format(amount);
  };

  return (
    <AdminLayout>
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl lg:text-3xl font-bold text-gray-900">
          Quản lý Đặt chỗ
        </h1>
        <p className="text-gray-600 mt-2 text-sm lg:text-base">
          Quản lý tất cả đặt chỗ trong hệ thống
        </p>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-6 mb-6">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Tổng đặt chỗ</CardTitle>
            <Calendar className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {stats.totalBookings.toLocaleString("vi-VN")}
            </div>
            <p className="text-xs text-muted-foreground">Tất cả đặt chỗ</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Đã xác nhận</CardTitle>
            <Users className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {stats.confirmedBookings.toLocaleString("vi-VN")}
            </div>
            <p className="text-xs text-muted-foreground">
              {stats.totalBookings
                ? Math.round(
                    (stats.confirmedBookings / stats.totalBookings) * 100
                  )
                : 0}
              % tổng số
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Chờ xử lý</CardTitle>
            <AlertTriangle className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {stats.pendingBookings.toLocaleString("vi-VN")}
            </div>
            <p className="text-xs text-muted-foreground">Cần xử lý</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Tổng doanh thu
            </CardTitle>
            <DollarSign className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {Math.round(stats.totalRevenue / 1000000)}M₫
            </div>
            <p className="text-xs text-muted-foreground">Từ đặt chỗ</p>
          </CardContent>
        </Card>
      </div>

      {/* Bookings Table */}
      <Card>
        <CardHeader>
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
            <div>
              <CardTitle className="text-lg lg:text-xl">
                Danh sách đặt chỗ
              </CardTitle>
              <CardDescription className="text-sm">
                Quản lý tất cả đặt chỗ trong hệ thống
              </CardDescription>
            </div>
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
              <div className="relative">
                <Search className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                <Input
                  placeholder="Tìm kiếm đặt chỗ..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10 w-full sm:w-64"
                />
              </div>
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger className="w-full sm:w-40">
                  <SelectValue placeholder="Trạng thái" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">Tất cả</SelectItem>
                  <SelectItem value="VALIDATION_PENDING">
                    Chờ xác thực
                  </SelectItem>
                  <SelectItem value="PENDING">Chờ xử lý</SelectItem>
                  <SelectItem value="CONFIRMED">Đã xác nhận</SelectItem>
                  <SelectItem value="PAYMENT_PENDING">
                    Chờ thanh toán
                  </SelectItem>
                  <SelectItem value="PAID">Đã thanh toán</SelectItem>
                  <SelectItem value="PAYMENT_FAILED">
                    Thanh toán thất bại
                  </SelectItem>
                  <SelectItem value="CANCELLED">Đã hủy</SelectItem>
                  <SelectItem value="FAILED">Thất bại</SelectItem>
                  <SelectItem value="VALIDATION_FAILED">
                    Xác thực thất bại
                  </SelectItem>
                </SelectContent>
              </Select>
              <Select value={typeFilter} onValueChange={setTypeFilter}>
                <SelectTrigger className="w-full sm:w-40">
                  <SelectValue placeholder="Loại dịch vụ" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">Tất cả</SelectItem>
                  <SelectItem value="FLIGHT">Chuyến bay</SelectItem>
                  <SelectItem value="HOTEL">Khách sạn</SelectItem>
                  <SelectItem value="COMBO">Combo</SelectItem>
                </SelectContent>
              </Select>
              <Button
                variant="outline"
                onClick={loadBookings}
                disabled={loading}
                className="flex items-center gap-2"
              >
                <RefreshCw
                  className={`h-4 w-4 ${loading ? "animate-spin" : ""}`}
                />
                Làm mới
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="border rounded-lg overflow-x-auto">
            <Table className="min-w-[800px]">
              <TableHeader>
                <TableRow>
                  <TableHead>Mã đặt chỗ</TableHead>
                  <TableHead>Khách hàng</TableHead>
                  <TableHead>Loại dịch vụ</TableHead>
                  <TableHead>Chi tiết</TableHead>
                  <TableHead>Ngày tạo</TableHead>
                  <TableHead>Số tiền</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead className="w-[50px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={8} className="text-center py-8">
                      <div className="flex items-center justify-center">
                        <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600"></div>
                        <span className="ml-2">Đang tải...</span>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : bookings?.content.length === 0 ? (
                  <TableRow>
                    <TableCell
                      colSpan={8}
                      className="text-center py-8 text-gray-500"
                    >
                      Không có dữ liệu
                    </TableCell>
                  </TableRow>
                ) : (
                  bookings?.content.map((booking) => (
                    <TableRow key={booking.bookingId}>
                      <TableCell className="font-medium">
                        {booking.bookingReference}
                      </TableCell>
                      <TableCell>
                        <div>
                          <div className="font-medium">
                            {(() => {
                              try {
                                const productDetails = JSON.parse(
                                  booking.productDetailsJson
                                );
                                const primaryGuest =
                                  productDetails.guests?.find(
                                    (guest: any) =>
                                      guest.guestType === "PRIMARY"
                                  );
                                return primaryGuest
                                  ? `${primaryGuest.firstName} ${primaryGuest.lastName}`
                                  : booking.userId;
                              } catch {
                                return booking.userId;
                              }
                            })()}
                          </div>
                          <div className="text-sm text-gray-500">
                            {(() => {
                              try {
                                const productDetails = JSON.parse(
                                  booking.productDetailsJson
                                );
                                const primaryGuest =
                                  productDetails.guests?.find(
                                    (guest: any) =>
                                      guest.guestType === "PRIMARY"
                                  );
                                return (
                                  primaryGuest?.email ||
                                  `Saga: ${booking.sagaState}`
                                );
                              } catch {
                                return `Saga: ${booking.sagaState}`;
                              }
                            })()}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>{getTypeBadge(booking.bookingType)}</TableCell>
                      <TableCell>
                        <div className="text-sm">
                          {booking.productDetailsJson && (
                            <div>
                              <div className="text-gray-600">
                                Chi tiết sản phẩm
                              </div>
                              <div className="text-xs text-gray-500 max-w-xs truncate">
                                {JSON.parse(booking.productDetailsJson)
                                  .hotelName ||
                                  JSON.parse(booking.productDetailsJson)
                                    .flightNumber ||
                                  "Thông tin chi tiết"}
                              </div>
                            </div>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        {new Date(booking.createdAt).toLocaleDateString(
                          "vi-VN"
                        )}
                      </TableCell>
                      <TableCell className="font-medium">
                        {formatCurrency(booking.totalAmount)}
                      </TableCell>
                      <TableCell>{getStatusBadge(booking.status)}</TableCell>
                      <TableCell>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem
                              onClick={() => openDetailDialog(booking)}
                            >
                              <Eye className="mr-2 h-4 w-4" />
                              Xem chi tiết
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => openStatusDialog(booking)}
                            >
                              <Edit className="mr-2 h-4 w-4" />
                              Cập nhật trạng thái
                            </DropdownMenuItem>
                            {booking.status !== "CANCELLED" &&
                              booking.status !== "FAILED" && (
                                <DropdownMenuItem
                                  className="text-red-600"
                                  onClick={() => openCancelDialog(booking)}
                                >
                                  <X className="mr-2 h-4 w-4" />
                                  Hủy đặt chỗ
                                </DropdownMenuItem>
                              )}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          {/* Pagination */}
          {bookings && bookings.totalPages > 1 && (
            <div className="flex items-center justify-between px-2">
              <div className="text-sm text-muted-foreground">
                Hiển thị {(currentPage - 1) * pageSize + 1} đến{" "}
                {Math.min(currentPage * pageSize, bookings.totalElements)} trong
                tổng {bookings.totalElements} kết quả
              </div>
              <div className="flex items-center space-x-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(1)}
                  disabled={currentPage === 1}
                >
                  Đầu
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(currentPage - 1)}
                  disabled={currentPage === 1}
                >
                  Trước
                </Button>
                <div className="flex items-center space-x-1">
                  <span className="text-sm">Trang</span>
                  <span className="text-sm font-medium">{currentPage}</span>
                  <span className="text-sm">của</span>
                  <span className="text-sm font-medium">
                    {bookings.totalPages}
                  </span>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(currentPage + 1)}
                  disabled={currentPage === bookings.totalPages}
                >
                  Sau
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(bookings.totalPages)}
                  disabled={currentPage === bookings.totalPages}
                >
                  Cuối
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Booking Detail Dialog */}
      <BookingDetailDialog
        booking={selectedBooking}
        open={showDetailDialog}
        onOpenChange={setShowDetailDialog}
      />

      {/* Status Update Dialog */}
      <Dialog open={showStatusDialog} onOpenChange={setShowStatusDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cập nhật trạng thái đặt chỗ</DialogTitle>
            <DialogDescription>
              Cập nhật trạng thái cho đặt chỗ{" "}
              {selectedBooking?.bookingReference}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <label className="text-sm font-medium">Trạng thái mới</label>
              <Select value={newStatus} onValueChange={setNewStatus}>
                <SelectTrigger>
                  <SelectValue placeholder="Chọn trạng thái" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="VALIDATION_PENDING">
                    Chờ xác thực
                  </SelectItem>
                  <SelectItem value="PENDING">Chờ xử lý</SelectItem>
                  <SelectItem value="CONFIRMED">Đã xác nhận</SelectItem>
                  <SelectItem value="PAYMENT_PENDING">
                    Chờ thanh toán
                  </SelectItem>
                  <SelectItem value="PAID">Đã thanh toán</SelectItem>
                  <SelectItem value="PAYMENT_FAILED">
                    Thanh toán thất bại
                  </SelectItem>
                  <SelectItem value="CANCELLED">Đã hủy</SelectItem>
                  <SelectItem value="FAILED">Thất bại</SelectItem>
                  <SelectItem value="VALIDATION_FAILED">
                    Xác thực thất bại
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <label className="text-sm font-medium">Lý do (tùy chọn)</label>
              <Textarea
                placeholder="Nhập lý do cập nhật trạng thái..."
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
              />
            </div>
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setShowStatusDialog(false)}
              disabled={actionLoading}
            >
              Hủy
            </Button>
            <Button
              onClick={handleStatusUpdate}
              disabled={actionLoading || !newStatus}
            >
              {actionLoading ? "Đang cập nhật..." : "Cập nhật"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Cancel Booking Dialog */}
      <Dialog open={showCancelDialog} onOpenChange={setShowCancelDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Hủy đặt chỗ</DialogTitle>
            <DialogDescription>
              Bạn có chắc chắn muốn hủy đặt chỗ{" "}
              {selectedBooking?.bookingReference}? Thao tác này không thể hoàn
              tác.
            </DialogDescription>
          </DialogHeader>

          <div>
            <label className="text-sm font-medium">Lý do hủy (tùy chọn)</label>
            <Textarea
              placeholder="Nhập lý do hủy đặt chỗ..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
            />
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setShowCancelDialog(false)}
              disabled={actionLoading}
            >
              Không hủy
            </Button>
            <Button
              variant="destructive"
              onClick={handleCancelBooking}
              disabled={actionLoading}
            >
              {actionLoading ? "Đang hủy..." : "Hủy đặt chỗ"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AdminLayout>
  );
}
