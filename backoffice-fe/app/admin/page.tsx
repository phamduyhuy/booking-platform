"use client";

import { useEffect, useState } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Users, Calendar, DollarSign, AlertTriangle } from "lucide-react";
import { AdminLayout } from "@/components/admin/admin-layout";
import { StatsCard } from "@/components/admin/stats-card";
import { RevenueChart } from "@/components/admin/revenue-chart";
import { BookingsTable } from "@/components/admin/bookings-table";
import {
  RecentActivity,
  ActivityItem,
} from "@/components/admin/recent-activity";
import { BookingService, RevenueAnalytics } from "@/services/booking-service";
import { CustomerService } from "@/services/customer-service";
import { format } from "date-fns";
import { vi } from "date-fns/locale";

export default function AdminDashboard() {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState([
    {
      title: "Tổng doanh thu",
      value: "0₫",
      change: "+0%",
      trend: "up" as const,
      icon: DollarSign,
      description: "Tổng doanh thu hệ thống",
    },
    {
      title: "Đặt chỗ mới",
      value: "0",
      change: "+0%",
      trend: "up" as const,
      icon: Calendar,
      description: "Tổng số đặt chỗ",
    },
    {
      title: "Khách hàng hoạt động",
      value: "0",
      change: "+0%",
      trend: "up" as const,
      icon: Users,
      description: "Người dùng đang hoạt động",
    },
    {
      title: "Tỷ lệ hủy",
      value: "0%",
      change: "0%",
      trend: "down" as const,
      icon: AlertTriangle,
      description: "Tỷ lệ hủy đặt chỗ",
    },
  ]);

  const [revenueData, setRevenueData] = useState<
    { month: string; revenue: number; bookings: number }[]
  >([]);
  const [recentActivities, setRecentActivities] = useState<ActivityItem[]>([]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [
          bookingSummary,
          customerStats,
          revenueAnalytics,
          recentBookings,
        ] = await Promise.all([
          BookingService.getBookingSummary(),
          CustomerService.getCustomerStatistics(),
          BookingService.getRevenueAnalytics(),
          BookingService.getBookings({ size: 5 }),
        ]);

        // Update Stats
        const cancellationRate =
          bookingSummary.totalBookings > 0
            ? (bookingSummary.cancelledBookings /
                bookingSummary.totalBookings) *
              100
            : 0;

        setStats([
          {
            title: "Tổng doanh thu",
            value: new Intl.NumberFormat("vi-VN", {
              style: "currency",
              currency: "VND",
            }).format(bookingSummary.totalRevenue),
            change: "+12.5%", // Placeholder for growth calculation
            trend: "up",
            icon: DollarSign,
            description: "Tổng doanh thu hệ thống",
          },
          {
            title: "Tổng đặt chỗ",
            value: bookingSummary.totalBookings.toLocaleString("vi-VN"),
            change: "+8.2%", // Placeholder
            trend: "up",
            icon: Calendar,
            description: "Tổng số đặt chỗ",
          },
          {
            title: "Khách hàng",
            value: customerStats.totalCustomers.toLocaleString("vi-VN"),
            change: "+15.3%", // Placeholder
            trend: "up",
            icon: Users,
            description: "Tổng số khách hàng",
          },
          {
            title: "Tỷ lệ hủy",
            value: `${cancellationRate.toFixed(1)}%`,
            change: "-0.8%", // Placeholder
            trend: "down",
            icon: AlertTriangle,
            description: "Tỷ lệ hủy đặt chỗ",
          },
        ]);

        // Update Revenue Chart
        // Map month number to label (e.g., 1 -> T1)
        const formattedRevenueData = revenueAnalytics.monthlyRevenue
          .map((item) => ({
            ...item,
            month: `T${item.month}`,
          }))
          .sort((a, b) =>
            a.month.localeCompare(b.month, undefined, { numeric: true })
          );

        setRevenueData(formattedRevenueData);

        // Update Recent Activity
        const activities: ActivityItem[] = recentBookings.content.map(
          (booking) => ({
            id: booking.bookingId,
            type: "booking",
            user: booking.userId || "Khách hàng", // Should fetch user name ideally
            action: `đã đặt ${
              booking.bookingType === "FLIGHT"
                ? "chuyến bay"
                : booking.bookingType === "HOTEL"
                ? "khách sạn"
                : "combo"
            }`,
            time: format(new Date(booking.createdAt), "HH:mm dd/MM/yyyy", {
              locale: vi,
            }),
            color: "blue",
            icon: undefined, // Will use default based on type
          })
        );
        setRecentActivities(activities);
      } catch (error) {
        console.error("Failed to fetch dashboard data", error);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  const quickActions = [
    {
      title: "Thêm chuyến bay",
      description: "Tạo chuyến bay mới",
      action: "add-flight",
    },
    {
      title: "Thêm khách sạn",
      description: "Đăng ký khách sạn mới",
      action: "add-hotel",
    },
    {
      title: "Xem báo cáo",
      description: "Báo cáo chi tiết",
      action: "view-reports",
    },
    {
      title: "Quản lý khuyến mãi",
      description: "Tạo mã giảm giá",
      action: "manage-promotions",
    },
  ];

  return (
    <AdminLayout>
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">Dashboard Quản trị</h1>
        <p className="text-gray-600 mt-2">
          Tổng quan hoạt động nền tảng BookingSmart
        </p>
      </div>

      {/* Stats Grid - Responsive */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-6 mb-6 lg:mb-8">
        {stats.map((stat, index) => (
          <StatsCard key={index} {...stat} />
        ))}
      </div>

      {/* Charts and Tables Row - Stack on mobile */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4 lg:gap-6 mb-6 lg:mb-8">
        {/* Revenue Chart */}
        <div className="xl:col-span-2">
          <RevenueChart data={revenueData} />
        </div>

        {/* Recent Activity */}
        <div className="xl:col-span-1">
          <RecentActivity activities={recentActivities} />
        </div>
      </div>

      {/* Quick Actions - Responsive grid */}
      <Card className="mb-6 lg:mb-8">
        <CardHeader>
          <CardTitle>Thao tác nhanh</CardTitle>
          <CardDescription>Các tác vụ thường dùng</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {quickActions.map((action, index) => (
              <Card
                key={index}
                className="hover:shadow-md transition-shadow cursor-pointer"
              >
                <CardContent className="p-4">
                  <h3 className="font-medium mb-2 text-sm lg:text-base">
                    {action.title}
                  </h3>
                  <p className="text-xs lg:text-sm text-gray-600 mb-3">
                    {action.description}
                  </p>
                  <Button size="sm" className="w-full text-xs lg:text-sm">
                    Thực hiện
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Recent Bookings */}
      <Card>
        <CardHeader>
          <CardTitle>Đặt chỗ gần đây</CardTitle>
          <CardDescription>Danh sách đặt chỗ mới nhất</CardDescription>
        </CardHeader>
        <CardContent>
          <BookingsTable />
        </CardContent>
      </Card>
    </AdminLayout>
  );
}
