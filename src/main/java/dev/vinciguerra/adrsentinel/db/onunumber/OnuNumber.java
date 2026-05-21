package dev.vinciguerra.adrsentinel.db.onunumber;

import java.util.Objects;
import org.hibernate.annotations.ColumnDefault;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entità JPA che rappresenta un Numero ONU (UN Number) secondo la normativa ADR.
 * <p>
 * Questa classe modella la singola riga della "Lista delle Merci Pericolose" (Tabella A dell'ADR).
 * Contiene tutte le informazioni chimico-fisiche e le restrizioni logistiche necessarie 
 * per calcolare la compatibilità, le esenzioni e i percorsi consentiti per il trasporto.
 * </p>
 * <h3>Design Architetturale:</h3>
 * <ul>
 * <li><b>Validazione Dichiarativa (JSR-380):</b> L'integrità dei dati è garantita tramite annotazioni 
 * (es. {@code @Pattern}, {@code @Min}). Questo assicura che i vincoli siano rispettati sia durante 
 * la deserializzazione JSON (frontend) sia prima della persistenza su database.</li>
 * <li><b>Normalizzazione del Ciclo di Vita:</b> Il metodo {@link #normalize()} intercetta i dati sporchi 
 * prima della validazione e dopo il caricamento dal DB, garantendo una pulizia silenziosa e sicura (Safe Trim).</li>
 * <li><b>Composite Business Key:</b> L'uguaglianza ({@link #equals(Object)}) è calcolata sulla combinazione 
 * di {@code onuCode} e {@code packingGroup}, rispecchiando la regola logistica per cui la stessa materia 
 * può avere gradi di pericolo differenti.</li>
 * </ul>
 *
 * @author Giovanni Vinciguerra
 * @version 2.0 (Refactored with Jakarta Validation)
 * @since 1.0
 */
@Entity
@Table(name = "onu_number")
public class OnuNumber {
	/**
     * Enum che rappresenta il Gruppo di Imballaggio (Packing Group - PG).
     * Indica il grado di pericolo della materia trasportata.
     */
	public enum PackingGroup {
		/** Pericolo molto elevato. */
		I,
		/** Pericolo medio. */
		II,
		/** Pericolo basso. */
		III,
		/** Non applicabile (es. gas o materie specifiche). */
		NONE
	}
	
	/**
     * Enum che definisce le restrizioni per il transito nelle gallerie stradali.
     * Le lettere (B, C, D, E) indicano il tipo di galleria interdetta.
     */
	public enum TunnelRestriction {
		B,
		C,
		D,
		E,
		B_D,
		B_E,
		C_D,
		C_E,
		D_E,
		NONE
	}
	
	/**
     * Enum per lo stato fisico della materia. Cruciale per determinare le tipologie di cisterne o imballaggi.
     */
	public enum PhysicalState {
		SOLID,
		LIQUID,
		GAS
	}
	
	/** Chiave primaria surrogata autogenerata. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	/**
	 * Il codice a 4 cifre identificativo della materia (es. "1203", "3077").
	 * <p>
	 * <b>Vincoli:</b> Non può essere vuoto e deve essere composto da esattamente 4 cifre.
	 * </p>
	 */
	@Column(
		name = "onu_code",
		nullable = false,
		length = 4
	)
	private String onuCode;
	/**
	 * La designazione ufficiale di trasporto (Nome tecnico autorizzato dall'ADR).
	 * <p>
	 * <b>Vincoli:</b> Non vuota con lunghezza tra 3 e 255 caratteri.
	 * </p>
	 */
	@Column(
		name = "name",
		nullable = false,
		length = 255
	)
	private String name;
	/**
	 * Stato fisico della materia. Salvato come stringa nel database. 
	 * Essendo un'enum, la validazione strutturale è intrinseca in Java.
	 */
	@Enumerated(EnumType.STRING)
    @Column(
    	name = "physical_state",
    	nullable = false,
    	length = 255
    )
	private PhysicalState physicalState;
	/**
	 * Numero di Identificazione del Pericolo (Codice Kemler).
	 * <p>
	 * <b>Nota di Dominio:</b> Se assente (es. esplosivi o merci non in cisterna), assume il valore "NONE".
	 * Se presente, deve essere di 2 o 3 cifre, eventualmente preceduto dalla lettera 'X' (divieto uso acqua).
	 * </p>
	 */
	@Column(
		name = "kemler_code",
		nullable = false,
		length = 4
	)
	@ColumnDefault("'NONE'")
	private String kemlerCode;
	/** 
	 * Il grado di pericolo associato a questo record.
	 * Se null verrà associato il grado massimo {@code I}
	 */
	@Enumerated(EnumType.STRING)
    @Column(
    	name = "packing_group",
    	nullable = false,
    	length = 255
    )
	@ColumnDefault("'I'")
    private PackingGroup packingGroup;
	/**
	 * Il divieto di transito in determinate gallerie. 
	 * Se null verrà associato il grado massimo {@code B}
	 */
	@Enumerated(EnumType.STRING)
    @Column(
    	name = "tunnel_restriction",
    	nullable = false,
    	length = 255
    )
	@ColumnDefault("'B'")
    private TunnelRestriction tunnelRestriction;
	/**
	 * Categoria di trasporto ai fini dell'esenzione ADR 1.1.3.6 (Regola dei 1000 punti).
	 * <p>
	 * <b>Vincolo Architetturale:</b> Utilizza la classe wrapper {@code Integer} invece del primitivo {@code int} 
	 * per impedire che, in caso di dato mancante dal frontend, Java assegni un pericolosissimo valore 
	 * di default pari a 0 (che in ADR significa "Rischio Massimo senza esenzioni").
	 * </p>
	 */
	@Column(
		name = "transport_category",
		nullable = false
	)
	private Integer transportCategory;
	/** La classe di pericolo principale. Essenziale per il calcolo delle compatibilità (Matrice di segregazione). */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "adr_class_id",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_un_number_adr_class")
	)
	private AdrClass adrClass;
	
	/**
	 * Metodo di normalizzazione del ciclo di vita (Lifecycle Callback).
	 * <p>
	 * Viene eseguito automaticamente da Hibernate in tre momenti cruciali:
	 * 1. Prima di una INSERT ({@code @PrePersist})
	 * 2. Prima di un UPDATE ({@code @PreUpdate})
	 * 3. Immediatamente dopo la lettura dal Database ({@code @PostLoad})
	 * </p>
	 * Il suo scopo è effettuare un "Safe Trim" (rimozione spazi accidentali) e gestire i valori 
	 * predefiniti o di fallback (es. assegnare "NONE" al Kemler vuoto) <b>prima</b> che scatti 
	 * la Jakarta Validation o che l'oggetto venga inviato al frontend.
	 */
	@PrePersist
	@PreUpdate
	private void normalize() {
		if(onuCode != null)
			onuCode = onuCode.trim();
		if(name != null) {
			name = name.replaceAll("[\\r\\n\\t]+", " ");
			name = name.replaceAll(" {2,}", " ");
			name = name.trim();
		}
		if(kemlerCode == null || kemlerCode.isBlank())
			kemlerCode = "NONE";
		else
			kemlerCode = kemlerCode.trim().toUpperCase();
		if(packingGroup == null)
			packingGroup = PackingGroup.I;
		if(tunnelRestriction == null)
			tunnelRestriction = TunnelRestriction.B;
	}
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getOnuCode() {
		return onuCode;
	}
	
	public void setOnuCode(String onuCode) {
		this.onuCode = onuCode;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public PhysicalState getPhysicalState() {
		return physicalState;
	}
	
	public void setPhysicalState(PhysicalState physicalState) {
		this.physicalState = physicalState;
	}
	
	public String getKemlerCode() {
		return kemlerCode;
	}
	
	public void setKemlerCode(String kemlerCode) {
		this.kemlerCode = kemlerCode;
	}
	
	public PackingGroup getPackingGroup() {
		return packingGroup;
	}
	
	public void setPackingGroup(PackingGroup packingGroup) {
		this.packingGroup = packingGroup;
	}
	
	public TunnelRestriction getTunnelRestriction() {
		return tunnelRestriction;
	}
	
	public void setTunnelRestriction(TunnelRestriction tunnelRestriction) {
		this.tunnelRestriction = tunnelRestriction;
	}
	
	public Integer getTransportCategory() {
		return transportCategory;
	}
	
	public void setTransportCategory(Integer transportCategory) {
		this.transportCategory = transportCategory;
	}
	
	public AdrClass getAdrClass() {
		return adrClass;
	}
	
	public void setAdrClass(AdrClass adrClass) {
		this.adrClass = adrClass;
	}
	
	/**
     * Calcola l'hash code basato sulla chiave di business composta: {@code onuCode} + {@code packingGroup}.
     */
	@Override
	public int hashCode() {
		return Objects.hash(onuCode, packingGroup);
	}
	
	/**
     * Verifica l'uguaglianza tra due numeri ONU.
     * <p>
     * L'uguaglianza forte è definita dalla coincidenza sia del codice ONU (es. 1230) 
     * sia del Gruppo di Imballaggio (es. PG II), poiché la stessa materia chimica 
     * può esistere con gradi di pericolo (e quindi regolamentazioni) differenti.
     * </p>
     */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OnuNumber other = (OnuNumber) obj;
		return Objects.equals(onuCode, other.onuCode) && packingGroup == other.packingGroup;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("OnuNumber [id=").append(id).append(", onuCode=").append(onuCode).append(", name=").append(name)
			.append(", physicalState=").append(physicalState).append(", kemlerCode=").append(kemlerCode)
			.append(", packingGroup=").append(packingGroup).append(", tunnelRestriction=").append(tunnelRestriction)
			.append(", transportCategory=").append(transportCategory).append("]");
		return builder.toString();
	}
}
