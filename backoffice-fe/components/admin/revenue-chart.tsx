"use client";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

const data = [
  { month: "T1", revenue: 2400000000, bookings: 1200 },
  { month: "T2", revenue: 1800000000, bookings: 980 },
  { month: "T3", revenue: 2200000000, bookings: 1100 },
  { month: "T4", revenue: 2800000000, bookings: 1400 },
  { month: "T5", revenue: 3200000000, bookings: 1600 },
  { month: "T6", revenue: 2900000000, bookings: 1450 },
  { month: "T7", revenue: 3500000000, bookings: 1750 },
  { month: "T8", revenue: 3100000000, bookings: 1550 },
  { month: "T9", revenue: 2700000000, bookings: 1350 },
  { month: "T10", revenue: 3300000000, bookings: 1650 },
  { month: "T11", revenue: 3800000000, bookings: 1900 },
  { month: "T12", revenue: 4200000000, bookings: 2100 },
];

interface RevenueChartProps {
  data?: {
    month: string;
    revenue: number;
    bookings: number;
  }[];
}

export function RevenueChart({ data }: RevenueChartProps) {
  // Default empty data if not provided
  const chartData = data || [];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Doanh thu theo tháng</CardTitle>
        <CardDescription>
          Biểu đồ doanh thu và số lượng đặt chỗ trong năm
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="month" />
            <YAxis yAxisId="left" />
            <YAxis yAxisId="right" orientation="right" />
            <Tooltip
              formatter={(value, name) => [
                name === "revenue"
                  ? `${(Number(value) / 1000000).toFixed(1)}M₫`
                  : value,
                name === "revenue" ? "Doanh thu" : "Đặt chỗ",
              ]}
            />
            <Line
              yAxisId="left"
              type="monotone"
              dataKey="revenue"
              stroke="#2563eb"
              strokeWidth={2}
              dot={{ fill: "#2563eb" }}
            />
            <Line
              yAxisId="right"
              type="monotone"
              dataKey="bookings"
              stroke="#16a34a"
              strokeWidth={2}
              dot={{ fill: "#16a34a" }}
            />
          </LineChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
