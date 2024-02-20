package usb;

import lombok.extern.slf4j.Slf4j;
import org.usb4java.*;

import java.nio.ByteBuffer;

@Slf4j
public class UsbDumper {

    public static void main(String[] args) throws InterruptedException {
        Context ctx = new Context();
        int initResult = LibUsb.init(ctx);
        if (initResult < 0) {
            throw new IllegalStateException("Unable to initialize libusb");
        }

        HandlerThread handler = new HandlerThread(ctx);
        handler.start();

        DeviceList devices = new DeviceList();
        int deviceListRes = LibUsb.getDeviceList(ctx, devices);
        if (deviceListRes < 0) {
            throw new IllegalStateException("Unable to get list of usb devices");
        }

        try {
            for (Device device : devices) {
                int deviceAddress = LibUsb.getDeviceAddress(device);
                int busNumber = LibUsb.getBusNumber(device);
                int portNumber = LibUsb.getPortNumber(device);
                DeviceDescriptor deviceDescriptor = new DeviceDescriptor();
                int deviceDescriptorRes = LibUsb.getDeviceDescriptor(device, deviceDescriptor);
                if (deviceDescriptorRes < 0) {
                    log.error("Unable to get device description for device: {}", device);
                    continue;
                }

                short vendorId = deviceDescriptor.idVendor();
                short productId = deviceDescriptor.idProduct();

                log.info("Found usb device: deviceAddress: {}, busNumber: {}, portNumber: {}, vendorId: {}, productId: {}",
                        deviceAddress, busNumber, portNumber, vendorId, productId);

                log.info("Dumping configurations: ");
                byte configLength = deviceDescriptor.bNumConfigurations();
                for (byte i = 0; i < configLength; i++) {

                    ConfigDescriptor configDescriptor = new ConfigDescriptor();

                    int configDescriptorRes = LibUsb.getConfigDescriptor(device, i, configDescriptor);
                    if (configDescriptorRes < 0) {
                        log.error("Unable to get config description for device with #{}", i);
                        continue;
                    }

                    try {
                        log.info("{}", configDescriptor.dump());
                    } finally {
                        LibUsb.freeConfigDescriptor(configDescriptor);
                    }
                }

                DeviceHandle deviceHandle = new DeviceHandle();
                int openRes = LibUsb.open(device, deviceHandle);
                if (openRes < 0) {
                    log.error("Unable to open usb device");
                    continue;
                }

                log.info("{}", deviceDescriptor.dump(deviceHandle));

                log.info("Trying to read: ");
                tryToRead(deviceHandle);

                LibUsb.close(deviceHandle);

                log.info("--------------------------------------");
            }
        } finally {
            handler.abort();
            handler.join();

            LibUsb.freeDeviceList(devices, true);
            LibUsb.exit(ctx);
        }
    }

    private static void tryToRead(DeviceHandle handle) {
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
    }

    public static class HandlerThread extends Thread {
        private volatile boolean isRunning;
        private Context ctx;

        public HandlerThread(Context ctx) {
            this.ctx = ctx;
        }

        public void abort() {
            isRunning = false;
        }

        @Override
        public void run() {
            while (isRunning) {
                int res = LibUsb.handleEvents(ctx);
                if (res != LibUsb.SUCCESS) {
                    log.error("Unable to handle events. Code: {}", res);
                    isRunning = false;
                }
            }
        }
    }
}
