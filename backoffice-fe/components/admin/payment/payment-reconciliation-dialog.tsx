"use client"

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { CheckCircle2, XCircle, AlertCircle, ArrowRight } from "lucide-react"

interface ReconciliationResult {
  paymentId: string
  paymentReference: string
  localStatus: string
  stripePaymentIntentId?: string
  stripeStatus?: string
  stripeAmount?: number
  stripeCurrency?: string
  stripeCreated?: number
  localAmount?: number
  localCurrency?: string
  localTransactionStatus?: string
  stripePaid?: boolean
  stripeRefunded?: boolean
  stripeAmountCaptured?: number
  stripeAmountRefunded?: number
  stripeFailureCode?: string
  stripeFailureMessage?: string
  mappedStripeStatus?: string
  statusMatches?: boolean
  transactionStatusMatches?: boolean
  amountMatches?: boolean
  currencyMatches?: boolean
  reconciled?: boolean
  discrepancies?: string[]
  needsAttention?: boolean
  autoUpdated?: boolean
  updatedFields?: string[]
  message?: string
  error?: string
  stripeErrorCode?: string
}

interface PaymentReconciliationDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  result: ReconciliationResult | null
}

export function PaymentReconciliationDialog({
  open,
  onOpenChange,
  result,
}: PaymentReconciliationDialogProps) {
  if (!result) return null

  const hasError = !!result.error
  const hasDiscrepancies = result.needsAttention
  const isSuccess = result.reconciled && !hasDiscrepancies && !hasError

  const formatAmount = (amountInCents: number | undefined, currency: string = "VND") => {
    if (amountInCents === undefined) return "N/A"
    const amount = amountInCents / 100
    return new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: currency.toUpperCase(),
    }).format(amount)
  }

  const formatDate = (timestamp: number | undefined) => {
    if (!timestamp) return "N/A"
    return new Date(timestamp * 1000).toLocaleString("vi-VN")
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {hasError ? (
              <>
                <XCircle className="h-5 w-5 text-red-500" />
                Lỗi Đối Soát
              </>
            ) : isSuccess ? (
              <>
                <CheckCircle2 className="h-5 w-5 text-green-500" />
                Đối Soát Thành Công
              </>
            ) : (
              <>
                <AlertCircle className="h-5 w-5 text-orange-500" />
                Phát Hiện Sự Khác Biệt
              </>
            )}
          </DialogTitle>
          <DialogDescription>
            Kết quả đối soát thanh toán với Stripe Gateway
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Payment Info */}
          <Card>
            <CardContent className="pt-6">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <p className="text-muted-foreground">Payment Reference</p>
                  <p className="font-mono font-medium">{result.paymentReference}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">Payment ID</p>
                  <p className="font-mono text-xs">{result.paymentId}</p>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Error Message */}
          {hasError && (
            <Card className="border-red-200 bg-red-50">
              <CardContent className="pt-6">
                <div className="flex items-start gap-3">
                  <XCircle className="h-5 w-5 text-red-500 mt-0.5" />
                  <div>
                    <p className="font-medium text-red-900">{result.error}</p>
                    {result.stripeErrorCode && (
                      <p className="text-sm text-red-700 mt-1">
                        Error Code: {result.stripeErrorCode}
                      </p>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Success Message */}
          {isSuccess && (
            <Card className="border-green-200 bg-green-50">
              <CardContent className="pt-6">
                <div className="flex items-start gap-3">
                  <CheckCircle2 className="h-5 w-5 text-green-500 mt-0.5" />
                  <div>
                    <p className="font-medium text-green-900">
                      {result.message || "Dữ liệu thanh toán đồng bộ hoàn toàn với Stripe"}
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Stripe Data */}
          {result.reconciled && !hasError && (
            <>
              <div>
                <h3 className="font-semibold mb-3 flex items-center gap-2">
                  <span className="h-2 w-2 bg-blue-500 rounded-full"></span>
                  Thông Tin Từ Stripe
                </h3>
                <Card>
                  <CardContent className="pt-6">
                    <div className="space-y-3 text-sm">
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <p className="text-muted-foreground">Payment Intent ID</p>
                          <p className="font-mono text-xs">{result.stripePaymentIntentId}</p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">Status</p>
                          <Badge variant="outline">{result.stripeStatus}</Badge>
                        </div>
                      </div>
                      <Separator />
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <p className="text-muted-foreground">Amount</p>
                          <p className="font-medium">
                            {formatAmount(result.stripeAmount, result.stripeCurrency)}
                          </p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">Currency</p>
                          <p className="font-medium uppercase">{result.stripeCurrency}</p>
                        </div>
                      </div>
                      {result.stripeCreated && (
                        <>
                          <Separator />
                          <div>
                            <p className="text-muted-foreground">Created At</p>
                            <p className="font-medium">{formatDate(result.stripeCreated)}</p>
                          </div>
                        </>
                      )}
                      {result.stripePaid !== undefined && (
                        <>
                          <Separator />
                          <div className="grid grid-cols-2 gap-4">
                            <div>
                              <p className="text-muted-foreground">Paid</p>
                              <Badge variant={result.stripePaid ? "default" : "secondary"}>
                                {result.stripePaid ? "Yes" : "No"}
                              </Badge>
                            </div>
                            <div>
                              <p className="text-muted-foreground">Refunded</p>
                              <Badge variant={result.stripeRefunded ? "destructive" : "secondary"}>
                                {result.stripeRefunded ? "Yes" : "No"}
                              </Badge>
                            </div>
                          </div>
                        </>
                      )}
                      {result.stripeFailureCode && (
                        <>
                          <Separator />
                          <div className="bg-red-50 p-3 rounded-md">
                            <p className="font-medium text-red-900 mb-1">Failure Details</p>
                            <p className="text-xs text-red-700">
                              <span className="font-medium">Code:</span> {result.stripeFailureCode}
                            </p>
                            {result.stripeFailureMessage && (
                              <p className="text-xs text-red-700 mt-1">
                                <span className="font-medium">Message:</span>{" "}
                                {result.stripeFailureMessage}
                              </p>
                            )}
                          </div>
                        </>
                      )}
                    </div>
                  </CardContent>
                </Card>
              </div>

              {/* Local Database Data */}
              <div>
                <h3 className="font-semibold mb-3 flex items-center gap-2">
                  <span className="h-2 w-2 bg-purple-500 rounded-full"></span>
                  Thông Tin Từ Database
                </h3>
                <Card>
                  <CardContent className="pt-6">
                    <div className="space-y-3 text-sm">
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <p className="text-muted-foreground">Payment Status</p>
                          <Badge variant="outline">{result.localStatus}</Badge>
                        </div>
                        <div>
                          <p className="text-muted-foreground">Transaction Status</p>
                          <Badge variant="outline">{result.localTransactionStatus}</Badge>
                        </div>
                      </div>
                      <Separator />
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <p className="text-muted-foreground">Amount</p>
                          <p className="font-medium">
                            {formatAmount(result.localAmount, result.localCurrency)}
                          </p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">Currency</p>
                          <p className="font-medium uppercase">{result.localCurrency}</p>
                        </div>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </div>

              {/* Comparison Results */}
              <div>
                <h3 className="font-semibold mb-3 flex items-center gap-2">
                  <span className="h-2 w-2 bg-orange-500 rounded-full"></span>
                  So Sánh Kết Quả
                </h3>
                <Card>
                  <CardContent className="pt-6">
                    <div className="space-y-2">
                      <div className="flex items-center justify-between py-2 px-3 bg-gray-50 rounded-md">
                        <span className="text-sm font-medium">Payment Status</span>
                        {result.statusMatches ? (
                          <Badge variant="outline" className="bg-green-50 text-green-700 border-green-200">
                            <CheckCircle2 className="h-3 w-3 mr-1" />
                            Khớp
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200">
                            <XCircle className="h-3 w-3 mr-1" />
                            Không khớp
                          </Badge>
                        )}
                      </div>
                      <div className="flex items-center justify-between py-2 px-3 bg-gray-50 rounded-md">
                        <span className="text-sm font-medium">Transaction Status</span>
                        {result.transactionStatusMatches ? (
                          <Badge variant="outline" className="bg-green-50 text-green-700 border-green-200">
                            <CheckCircle2 className="h-3 w-3 mr-1" />
                            Khớp
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200">
                            <XCircle className="h-3 w-3 mr-1" />
                            Không khớp
                          </Badge>
                        )}
                      </div>
                      <div className="flex items-center justify-between py-2 px-3 bg-gray-50 rounded-md">
                        <span className="text-sm font-medium">Amount</span>
                        {result.amountMatches ? (
                          <Badge variant="outline" className="bg-green-50 text-green-700 border-green-200">
                            <CheckCircle2 className="h-3 w-3 mr-1" />
                            Khớp
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200">
                            <XCircle className="h-3 w-3 mr-1" />
                            Không khớp
                          </Badge>
                        )}
                      </div>
                      <div className="flex items-center justify-between py-2 px-3 bg-gray-50 rounded-md">
                        <span className="text-sm font-medium">Currency</span>
                        {result.currencyMatches ? (
                          <Badge variant="outline" className="bg-green-50 text-green-700 border-green-200">
                            <CheckCircle2 className="h-3 w-3 mr-1" />
                            Khớp
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="bg-red-50 text-red-700 border-red-200">
                            <XCircle className="h-3 w-3 mr-1" />
                            Không khớp
                          </Badge>
                        )}
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </div>

              {/* Discrepancies */}
              {hasDiscrepancies && result.discrepancies && result.discrepancies.length > 0 && (
                <Card className="border-orange-200 bg-orange-50">
                  <CardContent className="pt-6">
                    <div className="flex items-start gap-3">
                      <AlertCircle className="h-5 w-5 text-orange-500 mt-0.5 flex-shrink-0" />
                      <div className="flex-1">
                        <p className="font-medium text-orange-900 mb-3">
                          Phát hiện {result.discrepancies.length} sự khác biệt:
                        </p>
                        <ul className="space-y-2">
                          {result.discrepancies.map((discrepancy, index) => (
                            <li
                              key={index}
                              className="text-sm text-orange-800 bg-white p-2 rounded border border-orange-200"
                            >
                              {discrepancy}
                            </li>
                          ))}
                        </ul>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              )}

              {/* Auto-update Info */}
              {result.autoUpdated && (
                <Card className="border-blue-200 bg-blue-50">
                  <CardContent className="pt-6">
                    <div className="flex items-start gap-3">
                      <ArrowRight className="h-5 w-5 text-blue-500 mt-0.5" />
                      <div>
                        <p className="font-medium text-blue-900 mb-2">Đã Tự Động Cập Nhật</p>
                        <p className="text-sm text-blue-700">
                          Hệ thống đã tự động đồng bộ dữ liệu từ Stripe về database.
                        </p>
                        {result.updatedFields && result.updatedFields.length > 0 && (
                          <div className="mt-2">
                            <p className="text-xs text-blue-600 font-medium">Các trường đã cập nhật:</p>
                            <div className="flex flex-wrap gap-1 mt-1">
                              {result.updatedFields.map((field, index) => (
                                <Badge key={index} variant="outline" className="text-xs">
                                  {field}
                                </Badge>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  </CardContent>
                </Card>
              )}
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
