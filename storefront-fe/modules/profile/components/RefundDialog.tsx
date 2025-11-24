import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/use-toast";
import { paymentService } from "@/modules/payment/service";

const refundSchema = z.object({
  reason: z.string().min(1, "Vui lòng nhập lý do hoàn tiền"),
});

interface RefundDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  bookingId: string;
  paymentIntentId: string;
  transactionId: string;
  amount: number;
  currency: string;
  onSuccess: () => void;
}

export function RefundDialog({
  open,
  onOpenChange,
  bookingId,
  paymentIntentId,
  transactionId,
  amount,
  currency,
  onSuccess,
}: RefundDialogProps) {
  const [isLoading, setIsLoading] = useState(false);
  const { toast } = useToast();

  const form = useForm<z.infer<typeof refundSchema>>({
    resolver: zodResolver(refundSchema),
    defaultValues: {
      reason: "",
    },
  });

  async function onSubmit(values: z.infer<typeof refundSchema>) {
    setIsLoading(true);
    try {
      await paymentService.createRefund({
        paymentIntentId,
        transactionId,
        amount: amount, // Full refund for now
        reason: values.reason,
        metadata: {
          bookingId,
          requestedBy: "user",
        },
      });

      toast({
        title: "Thành công",
        description: "Yêu cầu hoàn tiền đã được gửi thành công.",
      });
      onSuccess();
      onOpenChange(false);
    } catch (error) {
      console.error(error);
      toast({
        variant: "destructive",
        title: "Lỗi",
        description: "Không thể gửi yêu cầu hoàn tiền. Vui lòng thử lại sau.",
      });
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Yêu cầu hoàn tiền</DialogTitle>
          <DialogDescription>
            Gửi yêu cầu hoàn tiền cho đơn đặt chỗ này. Số tiền hoàn lại sẽ được
            xử lý theo chính sách hoàn hủy.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-4 items-center gap-4">
                <FormLabel className="text-right">Số tiền</FormLabel>
                <div className="col-span-3 font-medium">
                  {new Intl.NumberFormat("vi-VN", {
                    style: "currency",
                    currency: currency,
                  }).format(amount)}
                </div>
              </div>
              <FormField
                control={form.control}
                name="reason"
                render={({ field }) => (
                  <FormItem className="grid grid-cols-4 items-center gap-4">
                    <FormLabel className="text-right">Lý do</FormLabel>
                    <div className="col-span-3">
                      <FormControl>
                        <Textarea
                          placeholder="Nhập lý do hoàn tiền..."
                          className="resize-none"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </div>
                  </FormItem>
                )}
              />
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
              >
                Hủy
              </Button>
              <Button type="submit" disabled={isLoading}>
                {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Gửi yêu cầu
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
