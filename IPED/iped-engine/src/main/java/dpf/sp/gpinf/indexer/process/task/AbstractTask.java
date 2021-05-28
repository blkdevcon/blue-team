package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.HashMap;
import java.util.Properties;

import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.io.TimeoutException;
import dpf.sp.gpinf.indexer.parsers.util.CorruptedCarvedException;
import dpf.sp.gpinf.indexer.process.MimeTypesProcessingOrder;
import dpf.sp.gpinf.indexer.process.Statistics;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.Worker.STATE;
import iped3.ICaseData;
import iped3.IItem;

/**
 * Classe que representa uma tarefa de procesamento (assinatura, hash, carving,
 * indexação, etc).
 *
 * Cada Worker possui suas próprias instâncias das tarefas, assim normalmente há
 * várias instancias de uma mesma tarefa.
 *
 * Caso a tarefa produza um novo item (subitem de zip ou carving), ele deve ser
 * processado pelo Worker @see
 * dpf.sp.gpinf.indexer.process.Worker#processNewItem() A tarefa recebe 01 item
 * por vez para processar.
 *
 */
public abstract class AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(AbstractTask.class);

    /**
     * Worker que executará esta tarefa.
     */
    protected Worker worker;

    /**
     * Estatísticas que podem ser atualizadas pela tarefa.
     */
    protected Statistics stats;

    /**
     * Diretório de saída do processamento. A tarefa pode criar um subdiretório
     * contendo dados resultantes do seu processamento.
     */
    protected File output;

    /**
     * Representa o caso atual. As diferentes instâncias das tarefas podem armazenar
     * objetos compartilhados no mapa objectMap do caso.
     */
    protected ICaseData caseData;

    /**
     * Próxima tarefa que será executada no pipeline.
     */
    protected AbstractTask nextTask;

    private long taskTime;

    private HashMap<Integer, Long> subitemProcessingTime = new HashMap<Integer, Long>();

    public long getTaskTime() {
        return taskTime;
    }

    public void addSubitemProcessingTime(long time) {
        Long prevTime = subitemProcessingTime.get(worker.evidence.getId());
        if (prevTime == null) {
            prevTime = 0L;
        }
        prevTime += time;
        subitemProcessingTime.put(worker.evidence.getId(), prevTime);
    }

    /**
     * Define a próxima tarefa no pipeline.
     *
     * @param nextTask
     *            próxima tarefa
     */
    public void setNextTask(AbstractTask nextTask) {
        this.nextTask = nextTask;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
        if (worker != null) {
            this.stats = worker.stats;
            this.caseData = worker.caseData;
            this.output = worker.output;
        }
    }

    public boolean hasIpedDatasource() {
        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        for (File source : args.getDatasources()) {
            if (source.getName().endsWith(".iped")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Método de inicialização da tarefa. Chamado em cada instância da tarefa pelo
     * Worker no qual ela está instalada.
     *
     * @param confParams
     *            Parâmetros obtidos do arquivo de configuração principal
     * @param confDir
     *            Diretório que pode conter um arquivo avançado de configuração da
     *            tarefa
     * @throws Exception
     *             Se ocorreu erro durante a inicialização
     */
    abstract public void init(final Properties confParams, File confDir) throws Exception;

    /**
     * Método chamado ao final do processamento em cada tarefa instanciada. Pode
     * conter código de finalização da tarefa e liberação de recursos.
     *
     * @throws Exception
     *             Caso ocorra erro inesperado.
     */
    abstract public void finish() throws Exception;

    /**
     * Realiza o processamento do item pela tarefa.
     *
     * @param evidence
     *            Item a ser processado.
     * @throws Exception
     *             Caso ocorra erro inesperado.
     */
    abstract protected void process(IItem evidence) throws Exception;

    /**
     * Realiza o processamento do item na tarefa e o envia para a próxima tarefa.
     *
     * @param evidence
     *            Item a ser processado.
     * @throws Exception
     *             Caso ocorra erro inesperado.
     */
    public final void processAndSendToNextTask(IItem evidence) throws Exception {

        while (worker.state != STATE.RUNNING) {
            synchronized (worker) {
                if (worker.state == STATE.PAUSING)
                    worker.state = STATE.PAUSED;
            }
            Thread.sleep(1000);
        }

        if (this == worker.firstTask && !evidence.isQueueEnd()) {
            worker.itensBeingProcessed++;
        }

        AbstractTask prevTask = worker.runningTask;
        IItem prevEvidence = worker.evidence;
        worker.runningTask = this;
        worker.evidence = evidence;

        if (this.isEnabled() && (!evidence.isToIgnore() || processIgnoredItem())) {
            long t = System.nanoTime() / 1000;
            processMonitorTimeout(evidence);
            Long subitensTime = subitemProcessingTime.remove(evidence.getId());
            if (subitensTime == null) {
                subitensTime = 0L;
            }
            taskTime += System.nanoTime() / 1000 - t - subitensTime;
        }

        sendToNextTask(evidence);

        worker.evidence = prevEvidence;
        worker.runningTask = prevTask;

        // ESTATISTICAS
        if (nextTask == null && !evidence.isQueueEnd()) {
            evidence.dispose();
            stats.incProcessed();
            if (!evidence.isSubItem() && !evidence.isCarved() && !evidence.isDeleted() && evidence.isToSumVolume()) {
                stats.incActiveProcessed();
            }
            if (evidence.isToSumVolume()) {
                Long len = evidence.getLength();
                if (len == null) {
                    len = 0L;
                }
                stats.addVolume(len);
            }
        }
        
        if (nextTask == null && !evidence.isQueueEnd())
            worker.itensBeingProcessed--;
    }

    /**
     * Envia o item para a próxima tarefa que será executada.
     *
     * @param evidence
     *            Item a ser processado
     * @throws Exception
     *             Caso ocorra erro inesperado.
     */
    protected void sendToNextTask(IItem evidence) throws Exception {
        if (nextTask != null) {
            int priority = MimeTypesProcessingOrder.getProcessingPriority(evidence.getMediaType());
            if (priority <= caseData.getCurrentQueuePriority())
                nextTask.processAndSendToNextTask(evidence);
            else {
                evidence.dispose();
                caseData.addItemToQueue(evidence, priority);
                if (!evidence.isQueueEnd()) {
                    worker.itensBeingProcessed--;
                }
            }
        }
    }

    /**
     * Processa o item monitorando timeout durante parsing. Caso ocorra timeout, o
     * item é reprocessado na tarefa com um parser seguro, sem risco de timeout.
     *
     * @param evidence
     *            Item a ser procesado
     * @throws Exception
     *             Se ocorrer erro inesperado.
     */
    private void processMonitorTimeout(IItem evidence) throws Exception {
        try {
            if (!evidence.isQueueEnd() || processQueueEnd())
                this.process(evidence);

        } catch (TimeoutException e) {
            LOGGER.warn("{} TIMEOUT processing {} ({} bytes)\t{}", worker.getName(), evidence.getPath(), //$NON-NLS-1$
                    evidence.getLength(), e);
            stats.incTimeouts();
            evidence.setTimeOut(true);
            processMonitorTimeout(evidence);

        } catch (Throwable t) {
            // Ignora arquivos recuperados e corrompidos
            if (t.getCause() instanceof CorruptedCarvedException) {
                stats.incCarvedIgnored(evidence);
                evidence.setToIgnore(true, false);
                evidence.setAddToCase(false);
            } else {
                throw t;
            }

        }
    }

    /**
     * Indica se itens ignorados (hash:status = ignore), devem ser processados pela
     * tarefa ou não. O valor padrão é false, assim itens ignorados não são
     * processados pelas tarefas seguintes. Tarefas específicas podem sobrescrever
     * esse comportamento.
     *
     * @return se a tarefa deve processar um item ignorado.
     */
    protected boolean processIgnoredItem() {
        return false;
    }

    protected boolean processQueueEnd() {
        return false;
    }

    /**
     * Retorna se a tarefa está habilitada. Padrão é sim, mas pode ser sobrescrita
     * se a tarefa possuir esse controle.
     */
    public boolean isEnabled() {
        return true;
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * This method can be overwritten by concrete tasks to detect when the processing is interrupted
     * by the user (e.g. closing the application window) when the tasking *is running* (i.e. it is the 
     * active task of a worker), and release resources (e.g. stop external processes). 
     * Default implementation does nothing. 
     */
    public void interrupted() {
    }
}
