package usb;

import lombok.extern.slf4j.Slf4j;
import org.usb4java.*;

import java.nio.ByteBuffer;

@Slf4j
public class UsbReader {
    public static void main(String[] args) {
        Context ctx = new Context();
        int initResult = LibUsb.init(ctx);
        if (initResult < 0) {
            throw new IllegalStateException("Unable to initialize libusb");
        }

        short vendorId = 10522;
        short productId = 13603;
        final DeviceHandle handle = LibUsb.openDeviceWithVidPid(ctx, vendorId, productId);
        if (handle == null){
            log.error("Device not found.");
            System.exit(1);
        }

        ByteBuffer byteBuffer = BufferUtils.allocateByteBuffer(1024);
        Transfer transfer = LibUsb.allocTransfer();
        byte inEndpoint = (byte) 0x83;
        LibUsb.fillBulkTransfer(transfer, handle, inEndpoint, byteBuffer, new TransferCallback() {
            @Override
            public void processTransfer(Transfer transfer) {
                log.info("Transfer callback: actual: {}", transfer.actualLength());
            }
        }, null, 5000);
        int res = LibUsb.submitTransfer(transfer);
        if (res != LibUsb.SUCCESS) {
            log.error("Unable to read from device. Code: {}", res);
        }

//        int res = LibUsb.claimInterface(handle, 2);
//        if (res != LibUsb.SUCCESS) {
//            log.error("Unable to claim device interface. Res: {}", res);
//        }

//        LibUsb.releaseInterface(handle, 2);
        LibUsb.close(handle);
        LibUsb.exit(ctx);

        log.info("Done.");
    }
}
