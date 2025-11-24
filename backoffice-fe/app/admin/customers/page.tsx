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
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
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
  Search,
  MoreHorizontal,
  Eye,
  Edit,
  Ban,
  Users,
  UserCheck,
  UserX,
  Star,
  Plus,
  Trash2,
} from "lucide-react";
import { AdminLayout } from "@/components/admin/admin-layout";
import { CustomerService } from "@/services/customer-service";
import { CreateCustomerDialog } from "@/components/admin/create-customer-dialog";
import { EditCustomerDialog } from "@/components/admin/edit-customer-dialog";
import { CustomerDetailDialog } from "@/components/admin/customer-detail-dialog";
import { useToast } from "@/hooks/use-toast";
import type {
  Customer,
  PaginatedResponse,
  CustomerStatistics,
} from "@/types/api";

export default function AdminCustomers() {
  const [customers, setCustomers] =
    useState<PaginatedResponse<Customer> | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(
    null
  );
  const [currentPage, setCurrentPage] = useState(0);
  const { toast } = useToast();

  const [stats, setStats] = useState<CustomerStatistics>({
    totalCustomers: 0,
    activeCustomers: 0,
    newCustomersThisMonth: 0,
  });

  useEffect(() => {
    loadCustomers();
    loadStats();
  }, [searchTerm, currentPage]);

  const loadStats = async () => {
    try {
      const data = await CustomerService.getCustomerStatistics();
      setStats(data);
    } catch (error) {
      console.error("Failed to load stats:", error);
    }
  };

  const loadCustomers = async () => {
    try {
      setLoading(true);
      const data = await CustomerService.getCustomers({
        search: searchTerm || undefined,
        page: currentPage,
        size: 10,
      });
      setCustomers(data);
    } catch (error) {
      console.error("Failed to load customers:", error);
      toast({
        title: "Lỗi",
        description: "Không thể tải danh sách khách hàng",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleViewCustomer = (customer: Customer) => {
    setSelectedCustomer(customer);
    setDetailDialogOpen(true);
  };

  const handleEditCustomer = (customer: Customer) => {
    setSelectedCustomer(customer);
    setEditDialogOpen(true);
  };

  const handleDeleteCustomer = (customer: Customer) => {
    setSelectedCustomer(customer);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!selectedCustomer) return;

    try {
      await CustomerService.deleteCustomer(selectedCustomer.id);
      toast({
        title: "Thành công",
        description: "Đã xóa khách hàng thành công",
      });
      loadCustomers();
      loadStats();
      setDeleteDialogOpen(false);
      setSelectedCustomer(null);
    } catch (error) {
      toast({
        title: "Lỗi",
        description: "Không thể xóa khách hàng",
        variant: "destructive",
      });
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "ACTIVE":
        return <Badge className="bg-green-100 text-green-800">Hoạt động</Badge>;
      case "INACTIVE":
        return (
          <Badge className="bg-gray-100 text-gray-800">Không hoạt động</Badge>
        );
      case "BANNED":
        return <Badge className="bg-red-100 text-red-800">Bị khóa</Badge>;
      default:
        return <Badge variant="secondary">{status}</Badge>;
    }
  };

  const getTierBadge = (tier: string) => {
    switch (tier) {
      case "PLATINUM":
        return (
          <Badge className="bg-purple-100 text-purple-800">Platinum</Badge>
        );
      case "GOLD":
        return <Badge className="bg-yellow-100 text-yellow-800">Gold</Badge>;
      case "SILVER":
        return <Badge className="bg-gray-100 text-gray-800">Silver</Badge>;
      case "BRONZE":
        return <Badge className="bg-orange-100 text-orange-800">Bronze</Badge>;
      default:
        return <Badge variant="secondary">{tier}</Badge>;
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
          Quản lý Khách hàng
        </h1>
        <p className="text-gray-600 mt-2 text-sm lg:text-base">
          Quản lý thông tin và hoạt động của khách hàng
        </p>
      </div>

      {/* Stats Cards - Better responsive */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-6 mb-6">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Tổng khách hàng
            </CardTitle>
            <Users className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.totalCustomers}</div>
            <p className="text-xs text-muted-foreground">Đã đăng ký</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Khách hàng hoạt động
            </CardTitle>
            <UserCheck className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.activeCustomers}</div>
            <p className="text-xs text-muted-foreground">
              {stats.totalCustomers
                ? Math.round(
                    (stats.activeCustomers / stats.totalCustomers) * 100
                  )
                : 0}
              % tổng số
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Khách hàng mới
            </CardTitle>
            <Star className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              +{stats.newCustomersThisMonth}
            </div>
            <p className="text-xs text-muted-foreground">Trong tháng này</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              Khách hàng VIP
            </CardTitle>
            <UserX className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">0</div>
            <p className="text-xs text-muted-foreground">Gold & Platinum</p>
          </CardContent>
        </Card>
      </div>

      {/* Customers Table */}
      <Card>
        <CardHeader>
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
            <div>
              <CardTitle className="text-lg lg:text-xl">
                Danh sách khách hàng
              </CardTitle>
              <CardDescription className="text-sm">
                Quản lý thông tin và hoạt động của khách hàng
              </CardDescription>
            </div>
            <div className="flex items-center space-x-2">
              <div className="relative w-full lg:w-64">
                <Search className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                <Input
                  placeholder="Tìm kiếm khách hàng..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
              <Button onClick={() => setCreateDialogOpen(true)}>
                <Plus className="mr-2 h-4 w-4" />
                Thêm khách hàng
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="border rounded-lg overflow-x-auto">
            <Table className="min-w-[700px]">
              <TableHeader>
                <TableRow>
                  <TableHead>Khách hàng</TableHead>
                  <TableHead>Liên hệ</TableHead>
                  <TableHead>Ngày tham gia</TableHead>
                  <TableHead>Đặt chỗ</TableHead>
                  <TableHead>Chi tiêu</TableHead>
                  <TableHead>Hạng</TableHead>
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
                ) : customers?.content.length === 0 ? (
                  <TableRow>
                    <TableCell
                      colSpan={8}
                      className="text-center py-8 text-gray-500"
                    >
                      Không có dữ liệu
                    </TableCell>
                  </TableRow>
                ) : (
                  customers?.content.map((customer) => (
                    <TableRow key={customer.id}>
                      <TableCell>
                        <div className="flex items-center space-x-3">
                          <Avatar className="w-8 h-8">
                            <AvatarImage src="/placeholder.svg?height=32&width=32" />
                            <AvatarFallback>
                              {customer.name.charAt(0)}
                            </AvatarFallback>
                          </Avatar>
                          <div>
                            <div className="font-medium">{customer.name}</div>
                            <div className="text-sm text-gray-500">
                              {customer.id}
                            </div>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div>
                          <div className="text-sm">{customer.email}</div>
                          <div className="text-sm text-gray-500">
                            {customer.phone}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        {new Date(customer.createdAt).toLocaleDateString(
                          "vi-VN"
                        )}
                      </TableCell>
                      <TableCell className="font-medium">
                        {customer.totalBookings}
                      </TableCell>
                      <TableCell className="font-medium">
                        {formatCurrency(customer.totalSpent)}
                      </TableCell>
                      <TableCell>{getTierBadge(customer.tier)}</TableCell>
                      <TableCell>{getStatusBadge(customer.status)}</TableCell>
                      <TableCell>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem
                              onClick={() => handleViewCustomer(customer)}
                            >
                              <Eye className="mr-2 h-4 w-4" />
                              Xem chi tiết
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => handleEditCustomer(customer)}
                            >
                              <Edit className="mr-2 h-4 w-4" />
                              Chỉnh sửa
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              className="text-red-600"
                              onClick={() => handleDeleteCustomer(customer)}
                            >
                              <Trash2 className="mr-2 h-4 w-4" />
                              Xóa tài khoản
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>

      {/* Pagination */}
      {customers && customers.totalPages > 1 && (
        <div className="flex justify-center items-center space-x-2 mt-6">
          <Button
            variant="outline"
            onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
            disabled={currentPage === 0}
          >
            Trang trước
          </Button>
          <span className="text-sm text-gray-600">
            Trang {currentPage + 1} / {customers.totalPages}
          </span>
          <Button
            variant="outline"
            onClick={() =>
              setCurrentPage(
                Math.min(customers.totalPages - 1, currentPage + 1)
              )
            }
            disabled={currentPage >= customers.totalPages - 1}
          >
            Trang sau
          </Button>
        </div>
      )}

      {/* Dialogs */}
      <CreateCustomerDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
        onSuccess={loadCustomers}
      />

      <EditCustomerDialog
        customer={selectedCustomer}
        open={editDialogOpen}
        onOpenChange={setEditDialogOpen}
        onSuccess={loadCustomers}
      />

      <CustomerDetailDialog
        customer={selectedCustomer}
        open={detailDialogOpen}
        onOpenChange={setDetailDialogOpen}
        onEdit={() => {
          setDetailDialogOpen(false);
          setEditDialogOpen(true);
        }}
      />

      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn có chắc chắn muốn xóa khách hàng "{selectedCustomer?.name}"?
              Hành động này sẽ vô hiệu hóa tài khoản và không thể hoàn tác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete}>Xóa</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </AdminLayout>
  );
}
