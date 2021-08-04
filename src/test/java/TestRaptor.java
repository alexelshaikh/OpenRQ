import net.fec.openrq.*;
import net.fec.openrq.decoder.SourceBlockDecoder;
import net.fec.openrq.decoder.SourceBlockState;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.parameters.FECParameters;
import java.nio.ByteBuffer;
import java.util.Arrays;
public class TestRaptor {


    public static void main(String[] args) {
        byte[] source = new byte[10];
        Arrays.fill(source, (byte) 5);
        int symbolSize = 1;
        int numScrBlocks = 5;
        int numRepairSymbols = 2;
        int symbolOverhead = 0;

        //encode(null);
        FECParameters params = FECParameters.newParameters(source.length, symbolSize, numScrBlocks);

        System.out.println("source(" + source.length + "): " + Arrays.toString(source));

        byte[] encoded = encodeBytes(source, params, numRepairSymbols);
        System.out.println("encoded(" + encoded.length + "): " + Arrays.toString(encoded));

        byte[] decoded = decodeBytes(encoded, params, symbolOverhead);
        System.out.println("decoded(" + decoded.length + "): " + Arrays.toString(decoded));

        byte[] encoded_ = encodeWithParams(source, params, numRepairSymbols);
        System.out.println("encoded=" + Arrays.toString(encoded));

        byte[] decoded_ = decodeWithParams(encoded_, symbolOverhead);
        System.out.println("decoded=" + Arrays.toString(decoded_));
    }


    public static byte[] encodeWithParams(byte[] data, FECParameters params, int numRepairSymbols) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + (params.dataLengthAsInt() + (numRepairSymbols * params.numberOfSourceBlocks() / params.symbolSize())) * 9);
        params.writeTo(buffer);
        buffer.put(encodeBytes(data, params, numRepairSymbols));
        return buffer.array();
    }

    public static byte[] decodeWithParams(byte[] data, int symbolOverhead) {
        int offset = Long.BYTES + Integer.BYTES;
        ByteBuffer buffer = ByteBuffer.allocate(offset);
        buffer.putLong(ByteBuffer.wrap(data, 0, Long.BYTES).getLong());
        buffer.putInt(ByteBuffer.wrap(data, Long.BYTES, Integer.BYTES).getInt());
        buffer.flip();
        FECParameters params = FECParameters.parse(buffer).value();
        System.out.println("encoded=" + Arrays.toString(data));
        byte[] rawData = new byte[data.length - offset];
        int c = 0;
        for (int i=offset; i < data.length; i++) {
            rawData[i - offset] = data[i];
        }
        System.out.println(Arrays.toString(rawData));
        byte[] decoded = decodeBytes(rawData, params, symbolOverhead);
        return decoded;
    }

    public static byte[] encodeBytes(byte[] data, FECParameters params, int numRepairSymbols) {
        ArrayDataEncoder enc = OpenRQ.newEncoder(data, params);
        ByteBuffer bb = ByteBuffer.allocate((params.dataLengthAsInt() + (numRepairSymbols * params.numberOfSourceBlocks() / params.symbolSize())) * 9);

        for (SourceBlockEncoder sbEnc : enc.sourceBlockIterable()) {
            for (EncodingPacket encodingPacketSource : sbEnc.sourcePacketsIterable()) {
                //encodingPacketSource.writeTo(bb);
            }
            if (numRepairSymbols > 0) {
                for (EncodingPacket encodingPacketRepair : sbEnc.repairPacketsIterable(numRepairSymbols)) {
                    encodingPacketRepair.writeTo(bb);
                }
            }
        }
        return bb.array();
    }

    public static byte[] decodeBytes(byte[] data, FECParameters params, int symbolOverhead) {
        ArrayDataDecoder decoder = OpenRQ.newDecoder(params, symbolOverhead);
        SourceBlockDecoder latestBlockDecoder;
        Parsed<EncodingPacket> latestParse;
        EncodingPacket pkt;
        SourceBlockState decState;
        ByteBuffer bb = ByteBuffer.wrap(data);
        while (bb.hasRemaining() && !decoder.isDataDecoded()) {
            latestParse = decoder.parsePacket(bb, false);
            if (!latestParse.isValid())
                throw new RuntimeException("Parsed value is not valid! Error: " + latestParse.failureReason());
            else {
                pkt = latestParse.value();
                latestBlockDecoder = decoder.sourceBlock(pkt.sourceBlockNumber());
                decState = latestBlockDecoder.putEncodingPacket(pkt);
                if (decState == SourceBlockState.DECODING_FAILURE)
                    throw new RuntimeException("Decoding failure! decState: " + decState);
            }

        }
        return decoder.dataArray();
    }
}