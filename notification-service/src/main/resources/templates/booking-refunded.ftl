<#ftl encoding="UTF-8">
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8" />
    <title>Xác nhận hoàn tiền</title>
    <style>
        body { font-family: Arial, sans-serif; color: #1f2933; line-height: 1.6; }
        .wrapper { max-width: 640px; margin: 0 auto; padding: 24px; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; }
        .header { border-bottom: 1px solid #e5e7eb; margin-bottom: 20px; padding-bottom: 12px; }
        .section { margin-top: 18px; }
        .section-title { font-weight: 600; margin-bottom: 8px; color: #0f172a; }
        table { width: 100%; border-collapse: collapse; margin-top: 8px; }
        th, td { text-align: left; padding: 6px 0; }
        th { color: #475569; font-weight: 500; }
        .badge { display: inline-block; background: #0b7285; color: white; padding: 4px 10px; border-radius: 999px; font-size: 12px; letter-spacing: 0.06em; text-transform: uppercase; }
        .footer { margin-top: 24px; font-size: 12px; color: #6b7280; border-top: 1px solid #e5e7eb; padding-top: 12px; }
    </style>
</head>
<body>
<div class="wrapper">
    <div class="header">
        <span class="badge">Hoàn tiền thành công</span>
        <h2>Mã tham chiếu ${bookingReference!bookingId}</h2>
        <p>Số xác nhận: <strong>${confirmationNumber!"Đang chờ cấp"}</strong></p>
    </div>

    <p>Kính gửi ${contact.fullName!contact.firstName!"khách du lịch"},</p>

    <p>
        Yêu cầu hoàn tiền của bạn cho đặt chỗ <strong>${bookingReference!bookingId}</strong> đã được xử lý thành công.
        Số tiền hoàn lại sẽ được chuyển vào tài khoản thanh toán ban đầu của bạn trong vòng 5-10 ngày làm việc, tùy thuộc vào ngân hàng phát hành thẻ.
    </p>

    <div class="section">
        <div class="section-title">Chi tiết hoàn tiền</div>
        <table>
            <tr>
                <th>Số tiền hoàn lại</th>
                <td>${formattedTotalAmount!"Không có"}</td>
            </tr>
            <tr>
                <th>Phương thức hoàn tiền</th>
                <td>Thẻ tín dụng / Ghi nợ (Stripe)</td>
            </tr>
            <tr>
                <th>Trạng thái</th>
                <td>Đã hoàn tiền</td>
            </tr>
        </table>
    </div>

    <div class="section">
        <div class="section-title">Tóm tắt đặt chỗ đã hủy</div>
        <table>
            <tr>
                <th>Loại</th>
                <td>${bookingType!"Không có"}</td>
            </tr>
            <tr>
                <th>Tổng số tiền ban đầu</th>
                <td>${formattedTotalAmount!"Không có"}</td>
            </tr>
        </table>
    </div>

    <#if productDetails.flight??>
        <div class="section">
            <div class="section-title">Hành trình bay (Đã hủy)</div>
            <table>
                <tr>
                    <th>Chuyến bay</th>
                    <td>${productDetails.flight.flightNumber!"Không có"} · ${productDetails.flight.airline!"Không có"}</td>
                </tr>
                <tr>
                    <th>Tuyến đường</th>
                    <td>${productDetails.flight.originAirport!"??"} → ${productDetails.flight.destinationAirport!"??"}</td>
                </tr>
            </table>
        </div>
    </#if>

    <#if productDetails.hotel??>
        <div class="section">
            <div class="section-title">Hành trình khách sạn (Đã hủy)</div>
            <table>
                <tr>
                    <th>Khách sạn</th>
                    <td>${productDetails.hotel.hotelName!"Không có"} · ${productDetails.hotel.city!"Không có"}</td>
                </tr>
                <tr>
                    <th>Lưu trú</th>
                    <td>${productDetails.hotel.checkInDate!"Không có"} → ${productDetails.hotel.checkOutDate!"Không có"}</td>
                </tr>
            </table>
        </div>
    </#if>

    <div class="section">
        <p>
            Nếu bạn không nhận được tiền hoàn lại sau 10 ngày làm việc, vui lòng liên hệ với ngân hàng của bạn hoặc đội ngũ hỗ trợ của chúng tôi.
        </p>
    </div>

    <div class="footer">
        Cảm ơn bạn đã sử dụng dịch vụ của chúng tôi.<br/>
        Đội ngũ BookingSmart<br/>
        Email này đã được gửi đến ${contact.email!""}.
    </div>
</div>
</body>
</html>
