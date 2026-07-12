package dev.vinciguerra.adrsentinel.db.waybill;

import java.time.LocalDate;
import java.util.Objects;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entità di dominio (JPA) che rappresenta la persistenza fisica del Documento di Trasporto (D.D.T.) generato.
 * <p><b>Contesto Architetturale (Immutabilità e Append-Only):</b></p>
 * Questa classe è progettata secondo il pattern dell'Immutabilità. Tutti i campi persistenti 
 * sono marcati con {@code updatable = false} e mancano deliberatamente i metodi "setter". 
 * Nel dominio logistico, una volta che un D.D.T. viene consolidato e il relativo PDF generato, 
 * il suo stato storico e il suo contenuto binario non devono poter essere alterati.
 * <p><b>Design del Database:</b></p>
 * <ul>
 * <li><i>Vincoli di Unicità:</i> La tabella garantisce a livello di schema che non possano 
 * esistere due documenti con lo stesso numero D.D.T. ({@code uk_ddt_number}) o con 
 * lo stesso nome file ({@code uk_file_name}).</li>
 * <li><i>Storage Binario:</i> Il payload del documento è archiviato nativamente nel database 
 * sfruttando il dialetto specifico tramite {@code LONGBLOB}, capace di gestire file di grandi dimensioni.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0 (UUID Persistent Set Entity)
 * @since 1.0
 */
@Entity
@Table(
	name = "waybill",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_file_name", columnNames = {"filename"}),
		@UniqueConstraint(name = "uk_ddt_number", columnNames = {"ddt_number"})
	}
)
public class Waybill {
	/** Chiave primaria surrogata autogenerata. */
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	/**
	 * Identificativo univoco di business (Business Key) del D.D.T.
	 * Utilizzato per operazioni di tracciamento e per i contratti di uguaglianza dell'entità.
	 */
	@Column(
		name = "ddt_number",
		nullable = false,
		unique = true,
		updatable = false,
		length = 36
	)
	private String ddtNumber;
	/**
	 * Il nome fisico del file generato (es. "DDT-2026-001.pdf").
	 * Vincolato a 255 caratteri per compatibilità con i principali File System (NTFS, ext4).
	 */
	@Column(
		name = "filename",
		nullable = false,
		updatable = false,
		unique = true,
		length = 255
	)
	private String filename;
	/**
	 * Il MIME type del documento archiviato, fondamentale per il corretto invio del 
	 * payload tramite le API REST (es. "application/pdf").
	 */
	@Column(
		name = "content_type",
		nullable = false,
		updatable = false,
		length = 255
	)
	private String contentType;
	/**
	 * Lo stream di byte grezzo che costituisce il file vero e proprio.
	 * Mappato come {@code @Lob} e forzato a {@code LONGBLOB} per superare i limiti 
	 * standard dei campi blob leggeri e accomodare PDF multi-pagina.
	 */
	@Lob
    @Column(
    	name = "pdf_data",
    	nullable = false,
    	updatable = false,
    	columnDefinition = "LONGBLOB"
    )
	private byte[] pdfData;
	/**
	 * Timestamp di audit autogenerato al momento dell'istanziazione.
	 * Registra la data esatta di emissione e cristallizzazione del documento.
	 */
	@Column(
		name = "created_at",
		nullable = false,
		updatable = false
	)
	private LocalDate createdAt = LocalDate.now();
	/**
	 * Relazione uno-a-uno (OneToOne) unidirezionale e vincolante con l'entità Spedizione.
	 * Mappata con caricamento EAGER: il caricamento del file comporterà sempre il 
	 * recupero contestuale dei metadati di spedizione.
	 */
	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(
	    name = "shipment_id",
	    nullable = false,
	    updatable = false,
	    unique = true,
	    foreignKey = @ForeignKey(name = "fk_waybill_shipment")
	)
	private Shipment shipment;
	
	public Waybill() { /* volutamente lasciato vuoto per hibernate. */ }
	
	/**
	 * Costruttore parametrico dedicato esclusivamente alle operazioni di Query Projection (JPA/JPQL).
	 * <p><b>Contesto Architetturale (Memory Optimization):</b></p>
	 * Questo costruttore è stato implementato per supportare l'istanziazione parziale dell'entità 
	 * tramite espressioni <i>Constructor Expression</i> ({@code SELECT new ...}). Consente di caricare 
	 * in memoria unicamente i metadati anagrafici del Documento di Trasporto (D.D.T.), omettendo 
	 * deliberatamente il recupero dello stream binario ({@code pdfData}) e delle relazioni 
	 * strutturali (es. {@code shipment}). Questa strategia isola le letture leggere, prevenendo 
	 * picchi di consumo della Heap Memory (OutOfMemoryError) del server.
	 * <p><b>Avvertenze sullo Stato dell'Entità (Detached):</b></p>
	 * Le istanze generate tramite questo costruttore presentano caratteristiche architetturali specifiche:
	 * <ul>
	 * <li>Nascono nativamente in stato <b>Detached</b>: non sono tracciate dal <i>Persistence Context</i> 
	 * di Hibernate e non sono soggette al <i>Dirty Checking</i>.</li>
	 * <li>I campi omessi dalla firma (come {@code pdfData} e {@code shipment}) sono permanentemente 
	 * inizializzati a {@code null}. Invocare i relativi metodi "getter" restituirà un riferimento nullo.</li>
	 * </ul>
	 * <i>Nota: Questo costruttore non deve mai essere invocato manualmente per la creazione e la 
	 * successiva persistenza ({@code save}) di nuovi record nel database.</i>
	 * @param ddtNumber L'identificativo univoco di business del documento (es. "DDT-TRK-...").
	 * @param filename Il nome fisico e normalizzato del file.
	 * @param contentType Il MIME type associato al payload binario (es. "application/pdf").
	 * @param createdAt La data ufficiale di emissione e cristallizzazione del documento.
	 */
	public Waybill(String ddtNumber, String filename, String contentType, LocalDate createdAt) {
		super();
		this.ddtNumber = ddtNumber;
		this.filename = filename;
		this.contentType = contentType;
		this.createdAt = createdAt;
	}

	/**
	 * Costruttore parametrico completo per l'inizializzazione immutabile dell'entità.
	 * @param ddtNumber Il numero identificativo univoco del documento.
	 * @param filename Il nome del file destinato al salvataggio.
	 * @param contentType Il tipo MIME (application/pdf).
	 * @param pdfData Il payload binario del file PDF.
	 * @param createdAt La data di emissione.
	 * @param shipment La spedizione logistica a cui questo documento è legalmente legato.
	 */
	public Waybill(String ddtNumber, String filename, String contentType, byte[] pdfData, LocalDate createdAt,
			Shipment shipment) {
		super();
		this.ddtNumber = ddtNumber;
		this.filename = filename;
		this.contentType = contentType;
		this.pdfData = pdfData;
		this.createdAt = createdAt;
		this.shipment = shipment;
	}

	public Long getId() {
		return id;
	}

	public String getDdtNumber() {
		return ddtNumber;
	}

	public String getFilename() {
		return filename;
	}

	public String getContentType() {
		return contentType;
	}

	public byte[] getPdfData() {
		return pdfData;
	}

	public LocalDate getCreatedAt() {
		return createdAt;
	}

	public Shipment getShipment() {
		return shipment;
	}
	
	/** Genera l'hash code basato unicamente sulla Business Key immutabile ({@code ddtNumber}). */
	@Override
	public int hashCode() {
		return Objects.hash(ddtNumber);
	}
	
	/**
	 * Valuta l'uguaglianza logica tra due istanze basandosi unicamente sulla 
	 * Business Key ({@code ddtNumber}), rispettando il contratto di Hibernate.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Waybill other = (Waybill) obj;
		return Objects.equals(ddtNumber, other.ddtNumber);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Waybill [id=").append(id).append(", ddtNumber=").append(ddtNumber).append(", filename=")
			.append(filename).append(", contentType=").append(contentType).append(", createdAt=").append(createdAt)
			.append("]");
		return builder.toString();
	}
}
