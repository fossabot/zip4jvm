/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.olegcherednik.zip4jvm.io.zstd.frame;

import ru.olegcherednik.zip4jvm.io.zstd.Buffer;
import ru.olegcherednik.zip4jvm.io.zstd.CompressionParameters;
import ru.olegcherednik.zip4jvm.io.zstd.Histogram;
import ru.olegcherednik.zip4jvm.io.zstd.UnsafeUtil;
import ru.olegcherednik.zip4jvm.io.zstd.XxHash64;
import ru.olegcherednik.zip4jvm.io.zstd.huffman.HuffmanCompressionContext;
import ru.olegcherednik.zip4jvm.io.zstd.huffman.HuffmanCompressionTable;
import ru.olegcherednik.zip4jvm.io.zstd.huffman.HuffmanCompressor;

import static ru.olegcherednik.zip4jvm.io.zstd.Constants.COMPRESSED_BLOCK;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.COMPRESSED_LITERALS_BLOCK;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.MAGIC_NUMBER;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.MAX_BLOCK_SIZE;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.MIN_BLOCK_SIZE;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.MIN_WINDOW_LOG;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.RAW_BLOCK;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.RAW_LITERALS_BLOCK;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.RLE_LITERALS_BLOCK;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.SIZE_OF_BLOCK_HEADER;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.SIZE_OF_INT;
import static ru.olegcherednik.zip4jvm.io.zstd.Constants.TREELESS_LITERALS_BLOCK;
import static ru.olegcherednik.zip4jvm.io.zstd.Util.checkArgument;
import static ru.olegcherednik.zip4jvm.io.zstd.Util.put24BitLittleEndian;
import static ru.olegcherednik.zip4jvm.io.zstd.huffman.Huffman.MAX_SYMBOL;
import static ru.olegcherednik.zip4jvm.io.zstd.huffman.Huffman.MAX_SYMBOL_COUNT;

public class ZstdFrameCompressor {

    private static final int CHECKSUM_FLAG = 0b100;
    private static final int SINGLE_SEGMENT_FLAG = 0b100000;

    private static final int MINIMUM_LITERALS_SIZE = 63;

    // the maximum table log allowed for literal encoding per RFC 8478, section 4.2.1
    private static final int MAX_HUFFMAN_TABLE_LOG = 11;

    // visible for testing
    static int writeMagic(Buffer outputBase) {
        return outputBase.putInt(MAGIC_NUMBER);
    }

    // visible for testing
    static int writeFrameHeader(Buffer outputBase, int inputSize, int windowSize) {
        final int outputAddress = outputBase.getOffs();
        int contentSizeDescriptor = (inputSize >= 256 ? 1 : 0) + (inputSize >= 65536 + 256 ? 1 : 0);
        int frameHeaderDescriptor = (contentSizeDescriptor << 6) | CHECKSUM_FLAG; // dictionary ID missing

        boolean singleSegment = windowSize >= inputSize;
        if (singleSegment) {
            frameHeaderDescriptor |= SINGLE_SEGMENT_FLAG;
        }

        outputBase.putByte((byte)frameHeaderDescriptor);

        if (!singleSegment) {
            int base = Integer.highestOneBit(windowSize);

            int exponent = 32 - Integer.numberOfLeadingZeros(base) - 1;
            if (exponent < MIN_WINDOW_LOG) {
                throw new IllegalArgumentException("Minimum window size is " + (1 << MIN_WINDOW_LOG));
            }

            int remainder = windowSize - base;
            if (remainder % (base / 8) != 0) {
                throw new IllegalArgumentException("Window size of magnitude 2^" + exponent + " must be multiple of " + (base / 8));
            }

            // mantissa is guaranteed to be between 0-7
            int mantissa = remainder / (base / 8);
            int encoded = ((exponent - MIN_WINDOW_LOG) << 3) | mantissa;
            outputBase.putByte((byte)encoded);
        }

        switch (contentSizeDescriptor) {
            case 0:
                if (singleSegment)
                    outputBase.putByte((byte)inputSize);
                break;
            case 1:
                outputBase.putShort((short)(inputSize - 256));
                break;
            case 2:
                outputBase.putInt(inputSize);
                break;
            default:
                throw new AssertionError();
        }

        return outputBase.getOffs() - outputAddress;
    }

    // visible for testing
    static int writeChecksum(byte[] outputBase, int outputAddress, byte[] inputBase, int inputAddress, int inputLimit) {
        int inputSize = inputLimit - inputAddress;
        long hash = XxHash64.hash(0, inputBase, inputAddress, inputSize);
        UnsafeUtil.putInt(outputBase, outputAddress, (int)hash);
        return SIZE_OF_INT;
    }

    public static int compress(byte[] inputBase, Buffer outputBase, int compressionLevel) {
        int inputSize = inputBase.length;

        CompressionParameters parameters = CompressionParameters.compute(compressionLevel, inputSize);

        int output = 0;

        output += writeMagic(outputBase);
        output += writeFrameHeader(outputBase, inputSize, 1 << parameters.getWindowLog());
        output += compressFrame(inputBase, outputBase, parameters);
        output += writeChecksum(outputBase.getBuf(), output, inputBase, 0, inputBase.length);

        return output;
    }

    private static int compressFrame(byte[] inputBase, Buffer outputBase, CompressionParameters parameters) {
        int outputAddress = outputBase.getOffs();
        int windowSize = 1 << parameters.getWindowLog(); // TODO: store window size in parameters directly?
        int blockSize = Math.min(MAX_BLOCK_SIZE, windowSize);

        int outputSize = outputBase.getBuf().length - outputAddress;
        int remaining = inputBase.length - 0;

        int output = outputAddress;
        int input = 0;

        CompressionContext context = new CompressionContext(parameters, 0, remaining);

        do {
            int lastBlockFlag = blockSize >= remaining ? 1 : 0;
            blockSize = Math.min(blockSize, remaining);

            int compressedSize = 0;
            if (remaining > 0) {
                compressedSize = compressBlock(inputBase, input, blockSize, outputBase.getBuf(), output + SIZE_OF_BLOCK_HEADER,
                        outputSize - SIZE_OF_BLOCK_HEADER, context, parameters);
            }

            if (compressedSize == 0) { // block is not compressible
                checkArgument(blockSize + SIZE_OF_BLOCK_HEADER <= outputSize, "Output size too small");

                int blockHeader = lastBlockFlag | (RAW_BLOCK << 1) | (blockSize << 3);
                put24BitLittleEndian(outputBase.getBuf(), output, blockHeader);
                UnsafeUtil.copyMemory(inputBase, input, outputBase.getBuf(), output + SIZE_OF_BLOCK_HEADER, blockSize);
                compressedSize = SIZE_OF_BLOCK_HEADER + blockSize;
            } else {
                int blockHeader = lastBlockFlag | (COMPRESSED_BLOCK << 1) | (compressedSize << 3);
                put24BitLittleEndian(outputBase.getBuf(), output, blockHeader);
                compressedSize += SIZE_OF_BLOCK_HEADER;
            }

            input += blockSize;
            remaining -= blockSize;
            output += compressedSize;
            outputSize -= compressedSize;
        }
        while (remaining > 0);

        return output - outputAddress;
    }

    private static int compressBlock(byte[] inputBase, int inputAddress, int inputSize, byte[] outputBase, int outputAddress, int outputSize,
            CompressionContext context, CompressionParameters parameters) {
        if (inputSize < MIN_BLOCK_SIZE + SIZE_OF_BLOCK_HEADER + 1) {
            //  don't even attempt compression below a certain input size
            return 0;
        }

        context.blockCompressionState.enforceMaxDistance(inputAddress + inputSize, 1 << parameters.getWindowLog());
        context.sequenceStore.reset();

        int lastLiteralsSize = parameters.getStrategy()
                                         .getCompressor()
                                         .compressBlock(inputBase, inputAddress, inputSize, context.sequenceStore, context.blockCompressionState,
                                                 context.offsets, parameters);

        int lastLiteralsAddress = inputAddress + inputSize - lastLiteralsSize;

        // append [lastLiteralsAddress .. lastLiteralsSize] to sequenceStore literals buffer
        context.sequenceStore.appendLiterals(inputBase, lastLiteralsAddress, lastLiteralsSize);

        // convert length/offsets into codes
        context.sequenceStore.generateCodes();

        long outputLimit = outputAddress + outputSize;
        int output = outputAddress;

        int compressedLiteralsSize = encodeLiterals(
                context.huffmanContext,
                parameters,
                outputBase,
                output,
                (int)(outputLimit - output),
                context.sequenceStore.literalsBuffer,
                context.sequenceStore.literalsLength);
        output += compressedLiteralsSize;

        int compressedSequencesSize = SequenceEncoder.compressSequences(outputBase, output, (int)(outputLimit - output), context.sequenceStore,
                parameters.getStrategy(), context.sequenceEncodingContext);

        int compressedSize = compressedLiteralsSize + compressedSequencesSize;
        if (compressedSize == 0) {
            // not compressible
            return 0;
        }

        // Check compressibility
        int maxCompressedSize = inputSize - calculateMinimumGain(inputSize, parameters.getStrategy());
        if (compressedSize > maxCompressedSize) {
            return 0; // not compressed
        }

        // confirm repeated offsets and entropy tables
        context.commit();

        return compressedSize;
    }

    private static int encodeLiterals(
            HuffmanCompressionContext context,
            CompressionParameters parameters,
            byte[] outputBase,
            int outputAddress,
            int outputSize,
            byte[] literals,
            int literalsSize) {
        // TODO: move this to Strategy
        boolean bypassCompression = (parameters.getStrategy() == CompressionParameters.Strategy.FAST) && (parameters.getTargetLength() > 0);
        if (bypassCompression || literalsSize <= MINIMUM_LITERALS_SIZE) {
            return rawLiterals(outputBase, outputAddress, literals, literalsSize);
        }

        int headerSize = 3 + (literalsSize >= 1024 ? 1 : 0) + (literalsSize >= 16384 ? 1 : 0);

        checkArgument(headerSize + 1 <= outputSize, "Output buffer too small");

        int[] counts = new int[MAX_SYMBOL_COUNT]; // TODO: preallocate
        Histogram.count(literals, literalsSize, counts);
        int maxSymbol = Histogram.findMaxSymbol(counts, MAX_SYMBOL);
        int largestCount = Histogram.findLargestCount(counts, maxSymbol);

        int literalsAddress = 0;
        if (largestCount == literalsSize) {
            // all bytes in input are equal
            return rleLiterals(outputBase, outputAddress, literals, literalsSize);
        } else if (largestCount <= (literalsSize >>> 7) + 4) {
            // heuristic: probably not compressible enough
            return rawLiterals(outputBase, outputAddress, literals, literalsSize);
        }

        HuffmanCompressionTable previousTable = context.getPreviousTable();
        HuffmanCompressionTable table;
        int serializedTableSize;
        boolean reuseTable;

        boolean canReuse = previousTable.isValid(counts, maxSymbol);

        // heuristic: use existing table for small inputs if valid
        // TODO: move to Strategy
        boolean preferReuse = parameters.getStrategy().ordinal() < CompressionParameters.Strategy.LAZY.ordinal() && literalsSize <= 1024;
        if (preferReuse && canReuse) {
            table = previousTable;
            reuseTable = true;
            serializedTableSize = 0;
        } else {
            HuffmanCompressionTable newTable = context.borrowTemporaryTable();

            newTable.initialize(
                    counts,
                    maxSymbol,
                    HuffmanCompressionTable.optimalNumberOfBits(MAX_HUFFMAN_TABLE_LOG, literalsSize, maxSymbol),
                    context.getCompressionTableWorkspace());

            serializedTableSize = newTable.write(outputBase, outputAddress + headerSize, outputSize - headerSize, context.getTableWriterWorkspace());

            // Check if using previous huffman table is beneficial
            if (canReuse && previousTable.estimateCompressedSize(counts, maxSymbol) <= serializedTableSize + newTable.estimateCompressedSize(counts,
                    maxSymbol)) {
                table = previousTable;
                reuseTable = true;
                serializedTableSize = 0;
                context.discardTemporaryTable();
            } else {
                table = newTable;
                reuseTable = false;
            }
        }

        int compressedSize;
        boolean singleStream = literalsSize < 256;
        if (singleStream) {
            compressedSize = HuffmanCompressor.compressSingleStream(outputBase, outputAddress + headerSize + serializedTableSize,
                    outputSize - headerSize - serializedTableSize, literals, literalsAddress, literalsSize, table);
        } else {
            compressedSize = HuffmanCompressor.compress4streams(outputBase, outputAddress + headerSize + serializedTableSize,
                    outputSize - headerSize - serializedTableSize, literals, literalsAddress, literalsSize, table);
        }

        int totalSize = serializedTableSize + compressedSize;
        int minimumGain = calculateMinimumGain(literalsSize, parameters.getStrategy());

        if (compressedSize == 0 || totalSize >= literalsSize - minimumGain) {
            // incompressible or no savings

            // discard any temporary table we might have borrowed above
            context.discardTemporaryTable();

            return rawLiterals(outputBase, outputAddress, literals, literalsSize);
        }

        int encodingType = reuseTable ? TREELESS_LITERALS_BLOCK : COMPRESSED_LITERALS_BLOCK;

        // Build header
        switch (headerSize) {
            case 3: { // 2 - 2 - 10 - 10
                int header = encodingType | ((singleStream ? 0 : 1) << 2) | (literalsSize << 4) | (totalSize << 14);
                put24BitLittleEndian(outputBase, outputAddress, header);
                break;
            }
            case 4: { // 2 - 2 - 14 - 14
                int header = encodingType | (2 << 2) | (literalsSize << 4) | (totalSize << 18);
                UnsafeUtil.putInt(outputBase, outputAddress, header);
                break;
            }
            case 5: { // 2 - 2 - 18 - 18
                int header = encodingType | (3 << 2) | (literalsSize << 4) | (totalSize << 22);
                UnsafeUtil.putInt(outputBase, outputAddress, header);
                UnsafeUtil.putByte(outputBase, outputAddress + SIZE_OF_INT, (byte)(totalSize >>> 10));
                break;
            }
            default:  // not possible : headerSize is {3,4,5}
                throw new IllegalStateException();
        }

        return headerSize + totalSize;
    }

    private static int rleLiterals(byte[] outputBase, int outputAddress, byte[] inputBase, int inputSize) {
        int headerSize = 1 + (inputSize > 31 ? 1 : 0) + (inputSize > 4095 ? 1 : 0);

        switch (headerSize) {
            case 1: // 2 - 1 - 5
                UnsafeUtil.putByte(outputBase, outputAddress, (byte)(RLE_LITERALS_BLOCK | (inputSize << 3)));
                break;
            case 2: // 2 - 2 - 12
                UnsafeUtil.putShort(outputBase, outputAddress, (short)(RLE_LITERALS_BLOCK | (1 << 2) | (inputSize << 4)));
                break;
            case 3: // 2 - 2 - 20
                UnsafeUtil.putInt(outputBase, outputAddress, RLE_LITERALS_BLOCK | 3 << 2 | inputSize << 4);
                break;
            default:   // impossible. headerSize is {1,2,3}
                throw new IllegalStateException();
        }

        UnsafeUtil.putByte(outputBase, outputAddress + headerSize, UnsafeUtil.getByte(inputBase, 0));

        return headerSize + 1;
    }

    private static int calculateMinimumGain(int inputSize, CompressionParameters.Strategy strategy) {
        // TODO: move this to Strategy to avoid hardcoding a specific strategy here
        int minLog = strategy == CompressionParameters.Strategy.BTULTRA ? 7 : 6;
        return (inputSize >>> minLog) + 2;
    }

    private static int rawLiterals(byte[] outputBase, int outputAddress, byte[] inputBase, int inputSize) {
        int headerSize = 1;
        if (inputSize >= 32) {
            headerSize++;
        }
        if (inputSize >= 4096) {
            headerSize++;
        }

        switch (headerSize) {
            case 1:
                UnsafeUtil.putByte(outputBase, outputAddress, (byte)(RAW_LITERALS_BLOCK | (inputSize << 3)));
                break;
            case 2:
                UnsafeUtil.putShort(outputBase, outputAddress, (short)(RAW_LITERALS_BLOCK | (1 << 2) | (inputSize << 4)));
                break;
            case 3:
                put24BitLittleEndian(outputBase, outputAddress, RAW_LITERALS_BLOCK | (3 << 2) | (inputSize << 4));
                break;
            default:
                throw new AssertionError();
        }

        // TODO: ensure this test is correct
        UnsafeUtil.copyMemory(inputBase, 0, outputBase, outputAddress + headerSize, inputSize);

        return headerSize + inputSize;
    }

}
