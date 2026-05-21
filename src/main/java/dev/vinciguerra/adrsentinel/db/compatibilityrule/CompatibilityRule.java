package dev.vinciguerra.adrsentinel.db.compatibilityrule;

import java.util.Objects;
import org.hibernate.annotations.ColumnDefault;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.exception.BadRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.persistence.UniqueConstraint;

/**
 * Entità JPA che rappresenta una Regola di Compatibilità (Matrice di Segregazione) tra due Classi ADR.
 * <p>
 * Questa classe definisce se due diverse categorie di merci pericolose possono viaggiare insieme 
 * sullo stesso veicolo o se devono essere segregate.
 * </p>
 * <h3>Design Architetturale:</h3>
 * <ul>
 * <li><b>Assenza di Direzionalità:</b> La compatibilità tra la Classe A e la Classe B è bidirezionale. 
 * Per evitare dati duplicati o conflitti nel database, l'entità forza un ordine canonico prima del salvataggio 
 * (la classe minore diventa sempre la Classe A), proteggendo il vincolo di univocità {@code uk_class_a_class_b}.</li>
 * <li><b>Auto-Normalizzazione:</b> I dati testuali in ingresso vengono silenziamente ripuliti dai caratteri 
 * di formattazione spuri (es. tabulazioni, a capo multipli) generati dal copia-incolla dai manuali normativi.</li>
 * </ul>
 *
 * @author Giovanni Vinciguerra
 * @version 2.0
 * @since 1.0
 */
@Entity
@Table(
	name = "compatibility_rule",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_class_a_class_b", columnNames = {"adr_class_a_id", "adr_class_b_id"})
	}
)
public class CompatibilityRule {
	/** Chiave primaria surrogata autogenerata. */
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	/**
	 * La prima classe ADR coinvolta nella regola di compatibilità.
	 * <p>
	 * A seguito del processo di normalizzazione del ciclo di vita ({@link #prepareForPersist()}), 
	 * questa proprietà conterrà sempre la classe ADR con il {@code classCode} alfanumericamente inferiore 
	 * rispetto ad {@code adrClassB}.
	 * </p>
	 */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "adr_class_a_id", 
		nullable = false, 
		foreignKey = @ForeignKey(name = "fk_adr_compatibility_class_a")
	)
	private AdrClass adrClassA;
	/**
	 * La seconda classe ADR coinvolta nella regola di compatibilità.
	 * <p>
	 * A seguito del processo di normalizzazione, conterrà sempre la classe ADR 
	 * con il {@code classCode} alfanumericamente superiore rispetto ad {@code adrClassA}.
	 * </p>
	 */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "adr_class_b_id", 
		nullable = false, 
		foreignKey = @ForeignKey(name = "fk_adr_compatibility_class_b")
	)
	private AdrClass adrClassB;
	/**
	 * Flag booleano che determina la compatibilità operativa.
	 * <ul>
	 * <li>{@code true}: Il carico misto sul veicolo è consentito.</li>
	 * <li>{@code false}: Segregazione obbligatoria (divieto di carico in comune).</li>
	 * </ul>
	 * In assenza di specifica, il sistema adotta un approccio difensivo e nega la compatibilità (false).
	 */
	@Column(
		name = "is_compatible",
		nullable = false
	)
	@ColumnDefault("false")
	private boolean isCompatible = false;
	/**
	 * Nota operativa associata alla regola di compatibilità da mostrare nei documenti di trasporto.
	 * <p>
	 * Viene automaticamente convertita in MAIUSCOLO e privata di a capo/spaziature multiple per 
	 * garantire la perfetta impaginazione dei documenti di viaggio (CMR).
	 * </p>
	 */
	@Column(
		name = "warning_note",
		nullable = false,
		length = 255
	)
	@ColumnDefault("'" + WARNING_NOTE_GENERAL + "'")
	private String warningNote = WARNING_NOTE_GENERAL;
	
	/** Stringa di fallback predefinita qualora non vi siano note operative specifiche. */
	private static final String WARNING_NOTE_GENERAL = "NOTHING TO SAY";
	
	/**
	 * Hook Orchestratore (Coordinator) per gli eventi di scrittura del database.
	 * <p>
	 * Risolve la limitazione della specifica JPA che impedisce la definizione di multipli 
	 * metodi {@code @PreUpdate}/{@code @PrePersist} nella stessa entità.
	 * Definisce un ordine di esecuzione esplicito e deterministico per i processi di 
	 * validazione e normalizzazione prima della persistenza fisica.
	 * </p>
	 * </p>
	 * <b>Nota</b>: l'ordine di esecuzione dei metodi evita che si sprechi tempo di CPU per 
	 * la formattazione dei testi se la transazione sta per essere abortita a causa di una 
	 * violazione di uguaglianza (AdrClassA = AdrClassB).
	 * </p>
	 */
	@PrePersist
	@PreUpdate
	private void onBeforeSaveOrUpdate() {
		safeOrderForUniqueConstraint();
		normalize();
	}
	
	/**
     * Applica la regola: riordina le due classi sfruttando il metodo 
     * {@link AdrClass#compareTo(AdrClass)} in modo che la classe A sia sempre "minore" della classe B.
     * In questo modo, l'inserimento speculare (es. salvataggio di [8, 3] invece di [3, 8]) viene 
     * intercettato e riallineato per far scattare correttamente il vincolo di univocità del database.
     * @throws BadRequestException se si tenta di creare una regola tra due classi identiche.
     */
	private void safeOrderForUniqueConstraint() throws BadRequestException {
		if(adrClassA != null && adrClassB != null) {
			if(adrClassA.compareTo(adrClassB) > 0) {
				AdrClass temp = adrClassA;
				adrClassA = adrClassB;
				adrClassB = temp;
			} else if(adrClassA.equals(adrClassB))
				throw new BadRequestException("Class A and Class B cannot be the same ADR Class");
		}
	}
	
	/**
	 * Implementa il pattern "Tolerant Reader" e ha una duplice responsabilità:
	 * <ul>
	 * <li><b>Sanificazione Dati Storici:</b> Assicura che la nota operativa ({@code warningNote}) 
	 * sia normalizzata (trim, collasso spazi, upper-case) anche se nel database 
	 * risiedono dati "sporchi" o inseriti in passato manualmente via SQL.</li>
	 * <li><b>Fallback di Sicurezza:</b> Se il dato letto (o impostato a runtime) risulta nullo 
	 * o composto da soli spazi, applica istantaneamente il valore di default operativo.</li>
	 * </ul>
	 */
	private void normalize() {
		if(warningNote == null || warningNote.isBlank())
			warningNote = WARNING_NOTE_GENERAL;
		else {
			warningNote = warningNote.replaceAll("[\\r\\n\\t]+", " ");
			warningNote = warningNote.replaceAll(" {2,}", " ");
			warningNote = warningNote.trim().toUpperCase();
		}
	}
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public AdrClass getAdrClassA() {
		return adrClassA;
	}
	
	public void setAdrClassA(AdrClass adrClassA) {
		this.adrClassA = adrClassA;
	}
	
	public AdrClass getAdrClassB() {
		return adrClassB;
	}
	
	public void setAdrClassB(AdrClass adrClassB) {
		this.adrClassB = adrClassB;
	}
	
	public boolean isCompatible() {
		return isCompatible;
	}
	
	public void setCompatible(boolean isCompatible) {
		this.isCompatible = isCompatible;
	}
	
	public String getWarningNote() {
		return warningNote;
	}
	
	public void setWarningNote(String warningNote) {
		this.warningNote = warningNote;
	}
	
	/**
     * Calcola l'hash code dell'entità basandosi esclusivamente sulla combinazione delle due classi.
     */
	@Override
	public int hashCode() {
		return Objects.hash(adrClassA, adrClassB);
	}
	
	/**
     * Verifica l'uguaglianza tra due regole di compatibilità.
     * <p>
     * Grazie all'ordinamento canonico forzato prima del salvataggio, il confronto diretto 
     * tra le due coppie di classi risulta sufficiente e infallibile.
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
		CompatibilityRule other = (CompatibilityRule) obj;
		return Objects.equals(adrClassA, other.adrClassA) && Objects.equals(adrClassB, other.adrClassB);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("CompatibilityRule [id=").append(id).append(", isCompatible=").append(isCompatible)
			.append(", warningNote=").append(warningNote).append("]");
		return builder.toString();
	}
}
