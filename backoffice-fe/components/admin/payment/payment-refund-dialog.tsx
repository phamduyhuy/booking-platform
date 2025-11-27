"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { AlertTriangle, CreditCard } from "lucide-react";
import { PaymentService } from "@/services/payment-service";
import type { Payment } from "@/types/api";
import { toast } from "@/hooks/use-toast";

interface PaymentRefundDialogProps {
  payment: Payment | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onRefundSuccess: () => void;
}

export function PaymentRefundDialog({
  payment,
  open,
  onOpenChange,
  onRefundSuccess,
}: PaymentRefundDialogProps) {
  const [refundAmount, setRefundAmount] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);

  const handleRefund = async () => {
    if (!payment) return;

    try {
      setLoading(true);

      const amount =
        refundAmount && parseFloat(refundAmount) !== 0
          ? parseFloat(refundAmount)
          : undefined;

      // Validate amount
      if (amount && (amount <= 0 || amount > payment.amount)) {
        toast({
          title: "Error",
          description: "Số tiền hoàn trả không hợp lệ",
          variant: "destructive",
        });
        return;
      }

      await PaymentService.processRefund({
        paymentId: payment.paymentId,
        amount,
        reason: reason.trim() || undefined,
      });

      toast({
        title: "Success",
        description: "Yêu cầu hoàn tiền đã được xử lý thành công",
      });

      // Reset form
      setRefundAmount("");
      setReason("");
      onOpenChange(false);
      onRefundSuccess();
    } catch (error) {
      console.error("Failed to process refund:", error);
      toast({
        title: "Error",
        description: "Không thể xử lý hoàn tiền. Vui lòng thử lại.",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: "VND",
    }).format(amount);
  };

  if (!payment) return null;

  const maxRefundAmount = payment.amount;
  const isPartialRefund =
    refundAmount && parseFloat(refundAmount) < maxRefundAmount;
  const isFullRefund =
    !refundAmount || parseFloat(refundAmount) === maxRefundAmount;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <CreditCard className="h-5 w-5" />
            Hoàn tiền giao dịch
          </DialogTitle>
          <DialogDescription>
            Xử lý hoàn tiền cho giao dịch #{payment.paymentReference}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {/* Payment Info */}
          <div className="bg-gray-50 p-4 rounded-lg space-y-2">
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium">Mã giao dịch:</span>
              <span className="text-sm font-mono">
                {payment.paymentReference}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium">Số tiền gốc:</span>
              <span className="text-sm font-bold">
                {formatCurrency(payment.amount)}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium">Trạng thái:</span>
              <Badge className="bg-green-100 text-green-800">
                {payment.status}
              </Badge>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-sm font-medium">Nhà cung cấp:</span>
              <Badge variant="outline">{payment.provider}</Badge>
            </div>
          </div>

          {/* Refund Amount */}
          <div className="space-y-2">
            <Label htmlFor="refundAmount">Số tiền hoàn trả</Label>
            <div className="relative">
              <Input
                id="refundAmount"
                type="number"
                placeholder={`Tối đa ${formatCurrency(maxRefundAmount)}`}
                value={refundAmount}
                onChange={(e) => setRefundAmount(e.target.value)}
                max={maxRefundAmount}
                min={0}
                step={1000}
              />
              <div className="absolute right-3 top-3 text-xs text-gray-500">
                VND
              </div>
            </div>
            <div className="flex justify-between text-xs text-gray-500">
              <span>Để trống để hoàn toàn bộ</span>
              <span>Tối đa: {formatCurrency(maxRefundAmount)}</span>
            </div>
          </div>

          {/* Refund Type Indicator */}
          <div className="flex items-center gap-2 p-3 bg-blue-50 rounded-lg">
            <div className="w-2 h-2 bg-blue-500 rounded-full"></div>
            <span className="text-sm font-medium">
              {isFullRefund ? "Hoàn tiền toàn bộ" : "Hoàn tiền một phần"}
            </span>
            <Badge variant="outline" className="ml-auto">
              {refundAmount
                ? formatCurrency(parseFloat(refundAmount))
                : formatCurrency(maxRefundAmount)}
            </Badge>
          </div>

          {/* Reason */}
          <div className="space-y-2">
            <Label htmlFor="reason">Lý do hoàn tiền (tùy chọn)</Label>
            <Textarea
              id="reason"
              placeholder="Nhập lý do hoàn tiền..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
            />
          </div>

          {/* Warning */}
          <div className="flex items-start gap-3 p-3 bg-yellow-50 border-l-4 border-yellow-400 rounded-r-lg">
            <AlertTriangle className="h-5 w-5 text-yellow-600 mt-0.5" />
            <div className="text-sm text-yellow-800">
              <div className="font-medium">Lưu ý quan trọng:</div>
              <ul className="mt-1 space-y-1 text-xs">
                <li>
                  • Hoàn tiền sẽ được xử lý qua cùng phương thức thanh toán gốc
                </li>
                <li>• Thời gian xử lý có thể từ 3-7 ngày làm việc</li>
                <li>• Thao tác này không thể hoàn tác</li>
                {isPartialRefund && (
                  <li>
                    • Đây là hoàn tiền một phần, số tiền còn lại vẫn được giữ
                  </li>
                )}
              </ul>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={loading}
          >
            Hủy
          </Button>
          <Button
            onClick={handleRefund}
            disabled={
              loading ||
              (refundAmount !== "" &&
                parseFloat(refundAmount) !== 0 &&
                (parseFloat(refundAmount) <= 0 ||
                  parseFloat(refundAmount) > maxRefundAmount))
            }
            className="bg-red-600 hover:bg-red-700"
          >
            {loading
              ? "Đang xử lý..."
              : `Hoàn tiền ${
                  refundAmount
                    ? formatCurrency(parseFloat(refundAmount))
                    : formatCurrency(maxRefundAmount)
                }`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
