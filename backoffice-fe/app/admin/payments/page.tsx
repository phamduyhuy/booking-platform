"use client"

import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Search, MoreHorizontal, Eye, RefreshCw, Download, CreditCard, DollarSign, TrendingUp, AlertTriangle, Calendar } from "lucide-react"
import { AdminLayout } from "@/components/admin/admin-layout"
import { PaymentDetailDialog } from "@/components/admin/payment/payment-detail-dialog"
import { PaymentRefundDialog } from "@/components/admin/payment/payment-refund-dialog"
import { ManualPaymentDialog } from "@/components/admin/payment/ManualPaymentDialog"
import { PaymentReconciliationDialog } from "@/components/admin/payment/payment-reconciliation-dialog"
import { PaymentService } from "@/services/payment-service"
import type { Payment, PaymentStats, PaymentFilters, PaginatedResponse } from "@/types/api"
import { toast } from "@/hooks/use-toast"

export default function AdminPayments() {
  const [payments, setPayments] = useState<PaginatedResponse<Payment> | null>(null)
  const [stats, setStats] = useState<PaymentStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [statsLoading, setStatsLoading] = useState(true)
  const [searchTerm, setSearchTerm] = useState("")
  const [statusFilter, setStatusFilter] = useState<string>("all")
  const [providerFilter, setProviderFilter] = useState<string>("all")
  const [methodTypeFilter, setMethodTypeFilter] = useState<string>("all")
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize] = useState(20)
  
  // Dialog states
  const [selectedPayment, setSelectedPayment] = useState<Payment | null>(null)
  const [showDetailDialog, setShowDetailDialog] = useState(false)
  const [showRefundDialog, setShowRefundDialog] = useState(false)
  const [showManualPaymentDialog, setShowManualPaymentDialog] = useState(false)
  const [showReconciliationDialog, setShowReconciliationDialog] = useState(false)
  const [reconciliationResult, setReconciliationResult] = useState<any>(null)
  const [actionLoading, setActionLoading] = useState(false)

  useEffect(() => {
    setCurrentPage(1) // Reset to first page when filters change
  }, [searchTerm, statusFilter, providerFilter, methodTypeFilter])

  useEffect(() => {
    loadPayments()
    loadStats()
  }, [currentPage, searchTerm, statusFilter, providerFilter, methodTypeFilter])

  const loadPayments = async () => {
    try {
      setLoading(true)
      const filters: PaymentFilters = {
        search: searchTerm || undefined,
        status: statusFilter === "all" ? undefined : statusFilter as any,
        provider: providerFilter === "all" ? undefined : providerFilter as any,
        methodType: methodTypeFilter === "all" ? undefined : methodTypeFilter as any,
        page: currentPage,
        size: pageSize,
      }
      const data = await PaymentService.getPayments(filters)
      setPayments(data)
    } catch (error) {
      console.error("Failed to load payments:", error)
      toast({
        title: "Error",
        description: "Failed to load payments. Please try again.",
        variant: "destructive",
      })
    } finally {
      setLoading(false)
    }
  }

  const loadStats = async () => {
    try {
      setStatsLoading(true)
      const data = await PaymentService.getPaymentStats()
      setStats(data)
    } catch (error) {
      console.error("Failed to load payment stats:", error)
    } finally {
      setStatsLoading(false)
    }
  }

  const handleRetryPayment = async (paymentId: string) => {
    try {
      setActionLoading(true)
      await PaymentService.retryPayment(paymentId)
      
      toast({
        title: "Success",
        description: "Payment retry initiated successfully",
      })
      
      loadPayments() // Refresh the list
    } catch (error) {
      console.error("Failed to retry payment:", error)
      toast({
        title: "Error",
        description: "Failed to retry payment. Please try again.",
        variant: "destructive",
      })
    } finally {
      setActionLoading(false)
    }
  }

  const handleReconcilePayment = async (paymentId: string) => {
    try {
      setActionLoading(true)
      const result = await PaymentService.reconcilePayment(paymentId)
      
      // Show reconciliation dialog with full details
      setReconciliationResult(result)
      setShowReconciliationDialog(true)
      
      // If auto-updated, refresh the list
      if (result.autoUpdated) {
        loadPayments()
      }
      
    } catch (error) {
      console.error("Failed to reconcile payment:", error)
      toast({
        title: "Lỗi",
        description: "Không thể đối soát thanh toán. Vui lòng thử lại.",
        variant: "destructive",
      })
    } finally {
      setActionLoading(false)
    }
  }

  const handleExportPayments = async () => {
    try {
      const filters: PaymentFilters = {
        search: searchTerm || undefined,
        status: statusFilter === "all" ? undefined : statusFilter as any,
        provider: providerFilter === "all" ? undefined : providerFilter as any,
        methodType: methodTypeFilter === "all" ? undefined : methodTypeFilter as any,
      }
      
      const blob = await PaymentService.exportPayments(filters)
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `payments-${new Date().toISOString().split('T')[0]}.csv`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)

      toast({
        title: "Success",
        description: "Payments exported successfully",
      })
    } catch (error) {
      console.error("Failed to export payments:", error)
      toast({
        title: "Error",
        description: "Failed to export payments. Please try again.",
        variant: "destructive",
      })
    }
  }

  const openDetailDialog = (payment: Payment) => {
    setSelectedPayment(payment)
    setShowDetailDialog(true)
  }

  const openRefundDialog = (payment: Payment) => {
    setSelectedPayment(payment)
    setShowRefundDialog(true)
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "COMPLETED":
      case "CONFIRMED":
        return <Badge className="bg-green-100 text-green-800">Thành công</Badge>
      case "PENDING":
        return <Badge className="bg-yellow-100 text-yellow-800">Chờ xử lý</Badge>
      case "PROCESSING":
        return <Badge className="bg-blue-100 text-blue-800">Đang xử lý</Badge>
      case "FAILED":
      case "DECLINED":
        return <Badge className="bg-red-100 text-red-800">Thất bại</Badge>
      case "CANCELLED":
        return <Badge className="bg-gray-100 text-gray-800">Đã hủy</Badge>
      case "REFUND_COMPLETED":
        return <Badge className="bg-purple-100 text-purple-800">Đã hoàn tiền</Badge>
      case "REFUND_PENDING":
        return <Badge className="bg-orange-100 text-orange-800">Chờ hoàn tiền</Badge>
      case "TIMEOUT":
        return <Badge className="bg-red-100 text-red-800">Hết thời gian</Badge>
      default:
        return <Badge variant="secondary">{status}</Badge>
    }
  }

  const getProviderBadge = (provider: string) => {
    switch (provider) {
      case "STRIPE":
        return <Badge variant="outline" className="text-blue-600 border-blue-200">Stripe</Badge>
      case "VIETQR":
        return <Badge variant="outline" className="text-green-600 border-green-200">VietQR</Badge>
      case "MOMO":
        return <Badge variant="outline" className="text-pink-600 border-pink-200">MoMo</Badge>
      case "ZALOPAY":
        return <Badge variant="outline" className="text-blue-600 border-blue-200">ZaloPay</Badge>
      case "VNPAY":
        return <Badge variant="outline" className="text-orange-600 border-orange-200">VNPay</Badge>
      case "MANUAL":
        return <Badge variant="outline" className="text-gray-600 border-gray-200">Thủ công</Badge>
      default:
        return <Badge variant="outline">{provider}</Badge>
    }
  }

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: "VND",
    }).format(amount)
  }

  const totalPayments = payments?.totalElements || 0
  const successfulPayments = stats?.successfulPayments || 0
  const failedPayments = stats?.failedPayments || 0
  const pendingPayments = stats?.pendingPayments || 0
  const totalAmount = stats?.totalAmount || 0

  return (
    <AdminLayout>
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl lg:text-3xl font-bold text-gray-900">Quản lý Thanh toán</h1>
        <p className="text-gray-600 mt-2 text-sm lg:text-base">Quản lý tất cả giao dịch thanh toán trong hệ thống</p>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-6 mb-6">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Tổng giao dịch</CardTitle>
            <CreditCard className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{totalPayments}</div>
            <p className="text-xs text-muted-foreground">Tất cả giao dịch</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Thành công</CardTitle>
            <TrendingUp className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{successfulPayments}</div>
            <p className="text-xs text-muted-foreground">
              {totalPayments ? Math.round((successfulPayments / totalPayments) * 100) : 0}% tổng số
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Chờ xử lý</CardTitle>
            <AlertTriangle className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{pendingPayments}</div>
            <p className="text-xs text-muted-foreground">Cần xử lý</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Tổng giá trị</CardTitle>
            <DollarSign className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{Math.round(totalAmount / 1000000)}M₫</div>
            <p className="text-xs text-muted-foreground">Tổng doanh thu</p>
          </CardContent>
        </Card>
      </div>

      {/* Payments Table */}
      <Card>
        <CardHeader>
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
            <div>
              <CardTitle className="text-lg lg:text-xl">Danh sách giao dịch</CardTitle>
              <CardDescription className="text-sm">Quản lý tất cả giao dịch thanh toán trong hệ thống</CardDescription>
            </div>
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
              <div className="relative">
                <Search className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                <Input
                  placeholder="Tìm kiếm giao dịch..."
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
                  <SelectItem value="PENDING">Chờ xử lý</SelectItem>
                  <SelectItem value="PROCESSING">Đang xử lý</SelectItem>
                  <SelectItem value="COMPLETED">Thành công</SelectItem>
                  <SelectItem value="CONFIRMED">Đã xác nhận</SelectItem>
                  <SelectItem value="FAILED">Thất bại</SelectItem>
                  <SelectItem value="DECLINED">Bị từ chối</SelectItem>
                  <SelectItem value="CANCELLED">Đã hủy</SelectItem>
                  <SelectItem value="REFUND_COMPLETED">Đã hoàn tiền</SelectItem>
                </SelectContent>
              </Select>
              <Select value={providerFilter} onValueChange={setProviderFilter}>
                <SelectTrigger className="w-full sm:w-40">
                  <SelectValue placeholder="Nhà cung cấp" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">Tất cả</SelectItem>
                  <SelectItem value="STRIPE">Stripe</SelectItem>
                  <SelectItem value="VIETQR">VietQR</SelectItem>
                  <SelectItem value="MOMO">MoMo</SelectItem>
                  <SelectItem value="ZALOPAY">ZaloPay</SelectItem>
                  <SelectItem value="VNPAY">VNPay</SelectItem>
                  <SelectItem value="MANUAL">Thủ công</SelectItem>
                </SelectContent>
              </Select>
              <Button
                variant="outline"
                onClick={handleExportPayments}
                className="flex items-center gap-2"
              >
                <Download className="h-4 w-4" />
                Export
              </Button>
              <Button
                onClick={() => setShowManualPaymentDialog(true)}
                className="flex items-center gap-2"
              >
                <CreditCard className="h-4 w-4" />
                Tạo giao dịch
              </Button>
              <Button
                variant="outline"
                onClick={loadPayments}
                disabled={loading}
                className="flex items-center gap-2"
              >
                <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
                Làm mới
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="border rounded-lg overflow-x-auto">
            <Table className="min-w-[1000px]">
              <TableHeader>
                <TableRow>
                  <TableHead>Mã giao dịch</TableHead>
                  <TableHead>Booking ID</TableHead>
                  <TableHead>Số tiền</TableHead>
                  <TableHead>Nhà cung cấp</TableHead>
                  <TableHead>Phương thức</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Ngày tạo</TableHead>
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
                ) : payments?.content.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} className="text-center py-8 text-gray-500">
                      Không có dữ liệu
                    </TableCell>
                  </TableRow>
                ) : (
                  payments?.content.map((payment) => (
                    <TableRow key={payment.paymentId}>
                      <TableCell className="font-medium font-mono text-sm">
                        {payment.paymentReference}
                      </TableCell>
                      <TableCell className="font-mono text-sm">
                        {payment.bookingId.substring(0, 8)}...
                      </TableCell>
                      <TableCell className="font-medium">
                        {formatCurrency(payment.amount)}
                      </TableCell>
                      <TableCell>{getProviderBadge(payment.provider)}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{payment.methodType}</Badge>
                      </TableCell>
                      <TableCell>{getStatusBadge(payment.status)}</TableCell>
                      <TableCell>
                        {new Date(payment.createdAt * 1000).toLocaleDateString("vi-VN")}
                      </TableCell>
                      <TableCell>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => openDetailDialog(payment)}>
                              <Eye className="mr-2 h-4 w-4" />
                              Xem chi tiết
                            </DropdownMenuItem>
                            {(payment.status === "COMPLETED" || payment.status === "CONFIRMED") && (
                              <DropdownMenuItem onClick={() => openRefundDialog(payment)}>
                                <RefreshCw className="mr-2 h-4 w-4" />
                                Hoàn tiền
                              </DropdownMenuItem>
                            )}
                            {payment.status === "FAILED" && (
                              <DropdownMenuItem 
                                onClick={() => handleRetryPayment(payment.paymentId)}
                                disabled={actionLoading}
                              >
                                <RefreshCw className="mr-2 h-4 w-4" />
                                Thử lại
                              </DropdownMenuItem>
                            )}
                            <DropdownMenuItem 
                              onClick={() => handleReconcilePayment(payment.paymentId)}
                              disabled={actionLoading}
                            >
                              <Calendar className="mr-2 h-4 w-4" />
                              Đối soát
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

          {/* Pagination */}
          {payments && payments.totalPages > 1 && (
            <div className="flex items-center justify-between px-2 pt-4">
              <div className="text-sm text-muted-foreground">
                Hiển thị {((currentPage - 1) * pageSize) + 1} đến {Math.min(currentPage * pageSize, payments.totalElements)} trong tổng {payments.totalElements} kết quả
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
                  <span className="text-sm font-medium">{payments.totalPages}</span>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(currentPage + 1)}
                  disabled={currentPage === payments.totalPages}
                >
                  Sau
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(payments.totalPages)}
                  disabled={currentPage === payments.totalPages}
                >
                  Cuối
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Dialogs */}
      <PaymentDetailDialog
        payment={selectedPayment}
        open={showDetailDialog}
        onOpenChange={setShowDetailDialog}
      />

      <PaymentRefundDialog
        payment={selectedPayment}
        open={showRefundDialog}
        onOpenChange={setShowRefundDialog}
        onRefundSuccess={loadPayments}
      />

      <ManualPaymentDialog
        open={showManualPaymentDialog}
        onOpenChange={setShowManualPaymentDialog}
        onSuccess={loadPayments}
      />

      <PaymentReconciliationDialog
        open={showReconciliationDialog}
        onOpenChange={setShowReconciliationDialog}
        result={reconciliationResult}
      />
    </AdminLayout>
  )
}