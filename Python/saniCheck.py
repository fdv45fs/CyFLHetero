import py4cytoscape as p4c
import time

print("Đang thử kết nối với Cytoscape...")
try:
    # Ping Cytoscape
    result = p4c.cytoscape_ping()
    print(f"Kết nối thành công! Phản hồi từ Cytoscape: {result}")

    # Kiểm tra phiên bản Cytoscape
    version_info = p4c.cytoscape_version_info()
    print(f"Phiên bản Cytoscape: {version_info}")

    # Thử một lệnh đơn giản khác (ví dụ: lấy tên network hiện tại)
    # Đợi một chút để Cytoscape sẵn sàng
    time.sleep(1)
    try:
        current_network = p4c.get_network_name()
        if current_network:
            print(f"Network đang mở: {current_network}")
        else:
            print("Không có network nào đang mở.")
    except Exception as e_net:
        print(f"Không thể lấy tên network (có thể chưa có network nào): {e_net}")


except Exception as e:
    print(f"Kết nối thất bại hoặc có lỗi xảy ra: {e}")
    print("Hãy đảm bảo Cytoscape đang chạy và ứng dụng CyCaller đã được cài đặt.")
