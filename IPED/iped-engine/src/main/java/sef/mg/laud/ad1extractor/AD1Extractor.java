package sef.mg.laud.ad1extractor;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import iped3.io.SeekableInputStream;

/**
 *
 * @author guilherme.dutra
 */
public class AD1Extractor implements Closeable {

    static long assinatura_tam = 512; // 0x200

    private static Object lock = new Object();

    private File file;
    private Map<Integer, List<ByteBuffer>> fcMap = new HashMap<>();
    private List<FileChannel> channels = new ArrayList<>();

    long tamanho_bloco_arquivo = 0L;
    long numero_arquivo = 0L;
    long total_arquivos = 0L;
    static String charset = "UTF-8";

    private FileHeader rootHeader = null;

    byte vetor_15[] = new byte[15];
    byte vetor_17[] = new byte[17];
    byte vetor_20[] = new byte[20];
    byte vetor_512[] = new byte[512];
    byte vetor_48[] = new byte[48];
    byte vetor_variavel[];

    long PC = 0L;
    long aux = 0L;
    long PC_primeiro_arquivo = 0L;
    long PC_root = 0L;

    String assinatura = "";
    String nome_ad1 = "";
    long local_ad1_tam = 48L; // 0x30
    long nome_ad1_tam = 0L;
    long root_info_tam = 20L; // 0x14
    long root_nome_tam = 0L;

    public AD1Extractor(File arquivo) throws IOException {
        if (!arquivo.exists()) {
            throw new FileNotFoundException("Arquivo AD1 nao encontrado");
        }
        file = arquivo;
        FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        channels.add(fc);
        List<ByteBuffer> bbList = new ArrayList<>();
        for (long pos = 0; pos < fc.size(); pos += Integer.MAX_VALUE) {
            int size = (int) Math.min(fc.size() - pos, Integer.MAX_VALUE);
            bbList.add(fc.map(MapMode.READ_ONLY, pos, size));
        }
        fcMap.put(1, bbList);

        headerInit();
    }

    public void headerInit() throws IOException {

        // Procura assinatura AD1
        lerBytesArquivoAbsoluto(vetor_15, PC, 15);
        assinatura = new String(vetor_15, charset);

        if (!assinatura.equals("ADSEGMENTEDFILE")) {
            throw new AD1ExtractorException("Expected header not found in AD1: " + assinatura);
        }

        lerBytesArquivoAbsoluto(vetor_48, PC, 48);

        aux = lerTamanhoInteiroDeHexReverso(vetor_48, 35, 1);
        aux = aux >> 4;

        tamanho_bloco_arquivo = lerTamanhoInteiroDeHexReverso(vetor_48, 39, 4);
        tamanho_bloco_arquivo = (tamanho_bloco_arquivo << 4) + aux;

        numero_arquivo = lerTamanhoInteiroDeHexReverso(vetor_48, 28, 4);
        total_arquivos = lerTamanhoInteiroDeHexReverso(vetor_48, 32, 4);

        // Procurar Logical AD1
        lerBytesArquivoRelativo(vetor_17, PC, 17);
        nome_ad1 = new String(vetor_17, 0, 14, charset);
        if (!nome_ad1.equals("ADLOGICALIMAGE")) {
            throw new AD1ExtractorException("Expected signature not found in AD1: " + nome_ad1);
        }
        if (vetor_17[16] != 0x03) {
            ;// throw new AD1ExtractorException("AD1 version not supported: " +
             // vetor_17[16]);
        }

        lerBytesArquivoRelativo(vetor_48, PC, local_ad1_tam);
        PC_primeiro_arquivo = lerTamanhoInteiroDeHexReverso(vetor_48, 44, 8);
        PC_root = lerTamanhoInteiroDeHexReverso(vetor_48, 36, 8);

        /*
         * //Essa parte tem q mudar para versao mais nova do ftk imager PC +=
         * local_ad1_tam; //Ler nome da imagem ad1 nome_ad1_tam =
         * lerTamanhoInteiroDeHexReverso(vetor_48,48,4); vetor_variavel = new byte
         * [(int)(nome_ad1_tam)]; lerBytesArquivoRelativo(arquivo, vetor_variavel, PC,
         * nome_ad1_tam); nome_ad1 = String.valueOf(vetor_variavel);
         * 
         * vetor_variavel = null;
         * 
         * PC += nome_ad1_tam;
         */

        // Ler Root
        root_info_tam = 20L; // 0x14

        PC = PC_root;

        lerBytesArquivoRelativo(vetor_20, PC, root_info_tam);

        root_nome_tam = lerTamanhoInteiroDeHexReverso(vetor_20, 20, 4);
        vetor_variavel = new byte[(int) root_nome_tam];

        PC += root_info_tam;
        lerBytesArquivoRelativo(vetor_variavel, PC, root_nome_tam);
        String root_name = new String(vetor_variavel, charset);

        PC = PC_primeiro_arquivo;

        if (PC != 0L) {
            rootHeader = lerObjeto(PC, null);
        }

    }

    public FileHeader lerObjeto(long PC, FileHeader parent) throws IOException {

        byte vetor_48[] = new byte[48];

        byte vetor_16[] = new byte[16];
        byte vetor_variavel[];

        long info_objetos_tam = 48L; // 0x30

        long pedacos_tam_adicionais = 0L;

        // Ler Objeto

        FileHeader header = new FileHeader(this, parent);
        header.object_address = PC;

        lerBytesArquivoRelativo(vetor_48, PC, info_objetos_tam);
        PC += info_objetos_tam;

        // Os 8 primeiros bytes apontam para o proximo
        header.endereco_prox_objeto = lerTamanhoInteiroDeHexReverso(vetor_48, 8, 8);

        // Os proximos 8 bytes apontos para o filho
        header.endereco_filho_objeto = lerTamanhoInteiroDeHexReverso(vetor_48, 16, 8);

        // Os proximos 8 bytes tamanho do arquivo
        header.objeto_PC_fim_parcial = lerTamanhoInteiroDeHexReverso(vetor_48, 24, 8);

        // Tipo 0/2 arquivo, tipo 5/7 diretorio
        header.objeto_tipo = (int) lerTamanhoInteiroDeHexReverso(vetor_48, 44, 4);

        // Ler nome do arquivo
        header.nome_objeto_tam = lerTamanhoInteiroDeHexReverso(vetor_48, 48, 4);

        // Ler nome do objeto
        vetor_variavel = new byte[(int) header.nome_objeto_tam];

        lerBytesArquivoRelativo(vetor_variavel, PC, header.nome_objeto_tam);
        PC += header.nome_objeto_tam;

        header.objeto_nome = new String(vetor_variavel, charset);

        vetor_variavel = null;

        // Ler tamanho do objeto em bytes
        header.setObjetoTamanhoBytes(lerTamanhoInteiroDeHexReverso(vetor_48, 40, 8));

        // Aqui ficam os mapeamentos para os bytes stream do arquivo
        if (header.getFileSize() != 0) {

            lerBytesArquivoRelativo(vetor_16, PC, 16);
            PC += 16;

            header.objeto_pedacos_tam = lerTamanhoInteiroDeHexReverso(vetor_16, 16, 8);

            pedacos_tam_adicionais = 8 + 7; // mais 15
            vetor_variavel = new byte[(int) ((header.objeto_pedacos_tam * 8) + pedacos_tam_adicionais)];

            lerBytesArquivoRelativo(vetor_variavel, PC, (header.objeto_pedacos_tam * 8) + pedacos_tam_adicionais);
            PC += (header.objeto_pedacos_tam * 8) + pedacos_tam_adicionais;

            header.objeto_PC_ini_parcial = lerTamanhoInteiroDeHexReverso(vetor_variavel, 8, 8);
        } else {

            header.objeto_PC_ini_parcial = 0;
            header.objeto_pedacos_tam = 0;

        }

        if (header.objeto_pedacos_tam == 1) {

            header.adicionaPedaco(header.objeto_PC_ini_parcial, header.objeto_PC_fim_parcial);

        } else if (header.objeto_pedacos_tam > 1) {

            for (int i = 0; i < header.objeto_pedacos_tam; i++) {

                if (i != header.objeto_pedacos_tam - 1) {

                    header.adicionaPedaco(lerTamanhoInteiroDeHexReverso(vetor_variavel, ((i + 1) * 8), 8),
                            lerTamanhoInteiroDeHexReverso(vetor_variavel, ((i + 2) * 8), 8));

                } else {

                    header.adicionaPedaco(lerTamanhoInteiroDeHexReverso(vetor_variavel, ((i + 1) * 8), 8),
                            header.objeto_PC_fim_parcial);

                }

            }

        }

        vetor_variavel = null;

        if (parent != null) {
            header.caminho = parent.caminho;
        }
        header.caminho += "/" + header.objeto_nome;

        PC = header.objeto_PC_fim_parcial;

        lerPropriedade(PC, header);

        vetor_48 = null;

        vetor_16 = null;

        return header;

    }

    public void lerPropriedade(long endereco, FileHeader header) throws IOException {

        long endereco_prox_propriedade = 0L;
        long tamanho_propriedade = 20; // 0x14
        long propriedade_tam = 0;
        byte vetor_variavel[] = null;
        byte vetor_propriedade[] = null;
        String propriedade_extenso = "";
        long PC = 0;

        PC = endereco;

        vetor_propriedade = new byte[(int) tamanho_propriedade];

        lerBytesArquivoRelativo(vetor_propriedade, PC, tamanho_propriedade);
        PC += tamanho_propriedade;

        endereco_prox_propriedade = lerTamanhoInteiroDeHexReverso(vetor_propriedade, 8, 8);

        int propCode = (int) lerTamanhoInteiroDeHexReverso(vetor_propriedade, 16, 4);

        propriedade_tam = lerTamanhoInteiroDeHexReverso(vetor_propriedade, 20, 4);

        vetor_variavel = new byte[(int) propriedade_tam];

        lerBytesArquivoRelativo(vetor_variavel, PC, propriedade_tam);
        PC += propriedade_tam;

        propriedade_extenso = new String(vetor_variavel, charset);

        header.propriedadesMap.put(propCode, new Propriedade(propriedade_extenso));

        if (endereco_prox_propriedade != 0) {
            lerPropriedade(endereco_prox_propriedade, header);
        }

        vetor_variavel = null;
        vetor_propriedade = null;
        propriedade_extenso = null;

        return;
    }

    private static long lerTamanhoInteiroDeHexReverso(byte[] cbuf, int pos, int tam) {

        long r = 0L;

        for (int i = (pos - 1); i > ((pos - 1) - tam); i--) {
            r = (r << 8) + (cbuf[i] & 0xFF);
        }

        return r;

    }

    private void lerBytesArquivoAbsoluto(byte[] cbuf, long off, int len) throws IOException {
        // reads from first ad1 only
        ByteBuffer src = fcMap.get(1).get((int) (off / Integer.MAX_VALUE));
        src.duplicate().get(cbuf, (int) (off % Integer.MAX_VALUE), len);
    }

    private int seekAndRead(int ad1Ord, long seekOff, byte[] buf, int off, int len) throws IOException {

        try {
            List<ByteBuffer> bbList;
            synchronized (lock) {
                bbList = fcMap.get(ad1Ord);
                if (bbList == null) {
                    File newAd1 = new File(
                            file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(".") + 3) + ad1Ord);
                    FileChannel fc = FileChannel.open(newAd1.toPath(), StandardOpenOption.READ);
                    channels.add(fc);
                    bbList = new ArrayList<>();
                    for (long pos = 0; pos < fc.size(); pos += Integer.MAX_VALUE) {
                        int size = (int) Math.min(fc.size() - pos, Integer.MAX_VALUE);
                        bbList.add(fc.map(MapMode.READ_ONLY, pos, size));
                    }
                    fcMap.put(ad1Ord, bbList);
                }
            }
            ByteBuffer src = bbList.get((int) (seekOff / Integer.MAX_VALUE));
            int seek = (int) (seekOff % Integer.MAX_VALUE);
            ByteBuffer bb = src.duplicate();
            bb.position(seek);
            int size = Math.min(len, bb.remaining());
            bb.get(buf, off, size);
            return size;

        } catch (ClosedChannelException e) {
            synchronized (lock) {
                fcMap.put(ad1Ord, null);
            }
            throw e;
        }
    }

    private int lerBytesArquivoRelativo(byte[] cbuf, long off, long len) throws IOException {

        long endereco_final = assinatura_tam + off;
        // String arquivo_final = arquivo;
        long len_aux = len;
        long off_aux = off;
        long bloco_aux = ((tamanho_bloco_arquivo * 1024 * 1024) - assinatura_tam);
        int bytes_lidos = 0;
        int bytes_lidos_total = 0;
        int posicao_buffer = 0;

        while (len_aux > 0) {

            endereco_final = assinatura_tam + (off_aux % bloco_aux);

            int ad1File = (int) (off_aux / bloco_aux) + 1;

            bytes_lidos = seekAndRead(ad1File, endereco_final, cbuf, posicao_buffer, (int) len_aux);
            bytes_lidos_total += bytes_lidos;

            if (bytes_lidos < 0) {
                throw new IOException("Erro ao ler arquivo. Fim de arquivo inesperado.");
            }

            len_aux -= bytes_lidos;

            posicao_buffer += bytes_lidos;

            off_aux += bytes_lidos;

        }

        return bytes_lidos_total;

    }

    public SeekableInputStream getSeekableInputStream(FileHeader header) throws IOException {

        return new AD1SeekableInputstream(header);

    }

    public FileHeader getRootHeader() {
        return rootHeader;
    }

    public boolean isEncrypted() {
        return false;
    }

    @Override
    public void close() throws IOException {
        for (Closeable c : channels)
            c.close();
    }

    /**
     * 
     * @author guilherme.dutra
     * @author Luis Nassif
     */
    public class AD1SeekableInputstream extends SeekableInputStream {

        private int chunkSize = 65536; // 0x10000

        private Inflater inflater = null;
        private byte[] compressed_buffer = null;
        private int compressed_size = 0;
        private byte[] uncompressed_buffer = new byte[(int) chunkSize];
        private int uncompressed_size = -1;

        private FileHeader header;
        long position = 0;
        int lastInflatedChunk = -1;

        public AD1SeekableInputstream(FileHeader header) {
            this.header = header;
            inflater = new Inflater();
        }

        @Override
        public void seek(long pos) throws IOException {
            if (pos >= size())
                throw new IOException("Position requested larger than size");
            position = pos;
        }

        @Override
        public long position() throws IOException {
            return position;
        }

        @Override
        public long size() throws IOException {
            return header.getFileSize();
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int i;
            do {
                i = read(b, 0, 1);
            } while (i == 0);

            if (i == -1)
                return -1;
            else
                return b[0];
        }

        public long skip(long n) throws IOException {
            this.seek(position + n);
            return n;
        }

        public int read(byte buf[], int off, int len) throws IOException {

            if (position >= size())
                return -1;

            int chunk = (int) (position / chunkSize);
            int posInChunk = (int) (position % chunkSize);

            if (chunk != lastInflatedChunk) {

                Pedaco p = header.pedacosList.get(chunk);

                compressed_size = (int) (p.objeto_PC_fim - p.objeto_PC_ini);

                compressed_buffer = new byte[compressed_size];
                lerBytesArquivoRelativo(compressed_buffer, p.objeto_PC_ini, compressed_size);

                inflater.reset();
                inflater.setInput(compressed_buffer, 0, compressed_size);
                try {
                    uncompressed_size = inflater.inflate(uncompressed_buffer);

                } catch (DataFormatException e) {
                    throw new IOException(e);
                }

                lastInflatedChunk = chunk;
            }

            int available = uncompressed_size - posInChunk;
            int copyLen = len > available ? available : len;
            System.arraycopy(uncompressed_buffer, posInChunk, buf, off, copyLen);
            position += copyLen;

            return copyLen;
        }

        @Override
        public void close() {
            inflater.end();
        }

    }

}

class Propriedade {

    private String valor = "";

    Propriedade(String v) {
        this.valor = v;
    }

    public String getValor() {
        return this.valor;
    }

}

class Pedaco {

    public long objeto_PC_ini = 0L;
    public long objeto_PC_fim = 0L;

    public Pedaco(long ini, long fim) {
        this.objeto_PC_ini = ini;
        this.objeto_PC_fim = fim;
    }

}

class AD1ExtractorException extends IOException {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public AD1ExtractorException() {

    }

    public AD1ExtractorException(Exception source) {
        super(source);
    }

    public AD1ExtractorException(String message) {
        super(message);
    }

    public AD1ExtractorException(Throwable cause) {
        super(cause);
    }

    public AD1ExtractorException(String message, Throwable throwable) {
        super(message, throwable);
    }

}