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
	private static final String WARNING_NOTE_GENERAL = "Nothing to say";
	
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
     * @throws BadRequestException se si tenta di creare una regola tra due classi identiche, oppure se una delle due 
     * classi adr in questa regola di compatibilità è {@code null}.
     */
	private void safeOrderForUniqueConstraint() throws BadRequestException {
		if (adrClassA == null || adrClassB == null)
			throw new BadRequestException("Both ADR classes must be non-null to create a compatibility rule");
		else {
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
	 * @throws IllegalArgumentException Se la warningNote supera la lunghezza di 255 caratteri consentiti.
	 */
	private void normalize() throws IllegalArgumentException {
		if(warningNote == null || warningNote.isBlank())
			warningNote = WARNING_NOTE_GENERAL;
		else {
			warningNote = warningNote
				.replaceAll("[\\r\\n\\t]+", " ")
				.replaceAll(" {2,}", " ")
				.trim();
			if(warningNote.length() > 255)
				throw new IllegalArgumentException("warningNote exceeds max length of 255 characters");
			if(warningNote.isBlank())
				warningNote = WARNING_NOTE_GENERAL;
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
	 * Restituisce la classe ADR con il valore minore secondo l'ordinamento naturale
	 * definito da {@link AdrClass#compareTo(AdrClass)}.
	 * <p>
	 * Questo metodo viene utilizzato per costruire una rappresentazione
	 * <em>canonica</em> della coppia di classi ADR associata a una
	 * {@code CompatibilityRule}. L'obiettivo è rendere l'ordine delle due classi
	 * irrilevante durante le operazioni di confronto e di calcolo dell'hash,
	 * trattando le coppie {@code (A, B)} e {@code (B, A)} come logicamente
	 * equivalenti.
	 * </p>
	 * <p>
	 * Se entrambe le classi sono valorizzate, viene restituita quella che precede
	 * l'altra secondo l'ordinamento naturale implementato da
	 * {@link AdrClass#compareTo(AdrClass)}.
	 * </p>
	 * <p>
	 * Se uno dei due argomenti è {@code null}, viene restituito l'altro valore.
	 * Questa scelta evita la propagazione di {@link NullPointerException} durante
	 * la normalizzazione della coppia ed è coerente con il comportamento di
	 * {@link Objects#equals(Object, Object)} adottato dal metodo
	 * {@link #equals(Object)}.
	 * </p>
	 * <p>
	 * Questo metodo è un dettaglio implementativo interno e viene utilizzato
	 * esclusivamente per garantire che {@link #equals(Object)} e
	 * {@link #hashCode()} operino sempre sulla stessa rappresentazione canonica
	 * della regola di compatibilità.
	 * </p>
	 * @param a la prima classe ADR da confrontare; può essere {@code null}.
	 * @param b la seconda classe ADR da confrontare; può essere {@code null}.
	 * @return la classe ADR con il valore minore secondo l'ordinamento naturale,
	 * oppure l'unico valore non nullo se uno dei due argomenti è {@code null}.
	 */
	private AdrClass min(AdrClass a, AdrClass b) {
		if(a == null) return b;
		if(b == null) return a;
		
		return a.compareTo(b) <= 0 ? a : b;
	}
	
	/**
	 * Restituisce la classe ADR con il valore maggiore secondo l'ordinamento
	 * naturale definito da {@link AdrClass#compareTo(AdrClass)}.
	 * <p>
	 * Questo metodo rappresenta il complemento di {@link #min(AdrClass, AdrClass)}
	 * e viene utilizzato per ottenere il secondo elemento della rappresentazione
	 * canonica di una coppia di classi ADR.
	 * </p>
	 * <p>
	 * L'utilizzo congiunto dei metodi {@code min(...)} e {@code max(...)} consente
	 * di normalizzare qualsiasi coppia di classi ADR in una sequenza deterministica,
	 * rendendo equivalenti le rappresentazioni {@code (A, B)} e {@code (B, A)}.
	 * Tale normalizzazione è fondamentale affinché i metodi
	 * {@link #equals(Object)} e {@link #hashCode()} siano indipendenti
	 * dall'ordine con cui le classi vengono assegnate all'entità.
	 * </p>
	 * <p>
	 * Se entrambe le classi sono valorizzate, viene restituita quella che segue
	 * l'altra secondo l'ordinamento naturale implementato da
	 * {@link AdrClass#compareTo(AdrClass)}.
	 * </p>
	 * <p>
	 * Se uno dei due argomenti è {@code null}, viene restituito l'altro valore.
	 * Questa gestione garantisce un comportamento prevedibile anche nel caso di
	 * entità parzialmente inizializzate.
	 * </p>
	 * @param a la prima classe ADR da confrontare; può essere {@code null}.
	 * @param b la seconda classe ADR da confrontare; può essere {@code null}.
	 * @return la classe ADR con il valore maggiore secondo l'ordinamento naturale,
	 * oppure l'unico valore non nullo se uno dei due argomenti è {@code null}.
	 */
	private AdrClass max(AdrClass a, AdrClass b) {
	    if (a == null) return b;
	    if (b == null) return a;

	    return a.compareTo(b) >= 0 ? a : b;
	}
	
	/**
	 * Restituisce l'hash code della regola di compatibilità.
	 * <p>
	 * L'hash viene calcolato utilizzando la medesima rappresentazione canonica
	 * adottata da {@link #equals(Object)}. Le due classi ADR vengono pertanto
	 * ordinate preventivamente e solo successivamente utilizzate per il calcolo
	 * dell'hash.
	 * </p>
	 * <p>
	 * Questo garantisce il rispetto del contratto tra {@code equals()} e
	 * {@code hashCode()}, assicurando che due regole logicamente equivalenti,
	 * come {@code (A, B)} e {@code (B, A)}, producano sempre lo stesso valore
	 * di hash.
	 * </p>
	 * @return l'hash code della regola di compatibilità calcolato sulla coppia
	 * canonica delle classi ADR.
	 */
	@Override
	public int hashCode() {
		AdrClass first = min(adrClassA, adrClassB);
	    AdrClass second = max(adrClassA, adrClassB);
	    
		return Objects.hash(first, second);
	}
	
	/**
	 * Verifica l'uguaglianza tra due istanze di {@code CompatibilityRule}.
	 * <p>
	 * Una regola di compatibilità rappresenta una relazione <strong>non orientata</strong>
	 * tra due classi ADR. Di conseguenza, una regola composta dalla coppia
	 * {@code (A, B)} è logicamente equivalente alla coppia {@code (B, A)} e le due
	 * istanze devono essere considerate uguali indipendentemente dall'ordine con cui
	 * le classi sono state assegnate.
	 * </p>
	 * <p>
	 * Per garantire questa proprietà, il confronto non viene eseguito direttamente
	 * sui campi {@code adrClassA} e {@code adrClassB}. Entrambe le coppie vengono
	 * preventivamente trasformate nel rispettivo <em>ordine canonico</em>, ottenuto
	 * ordinando le due classi tramite i metodi di supporto {@code min(...)} e
	 * {@code max(...)}. Solo dopo questa normalizzazione vengono confrontati i
	 * rispettivi elementi.
	 * </p>
	 * <p>
	 * Grazie a questo approccio, le seguenti coppie risultano equivalenti:
	 * </p>
	 * <pre>{@code
	 * (Classe 3, Classe 8) == (Classe 8, Classe 3)
	 * }</pre>
	 * <p>
	 * L'uguaglianza risulta pertanto indipendente dall'ordine di inserimento delle
	 * classi e rimane valida anche per entità transienti, ossia prima
	 * dell'esecuzione del callback {@code @PrePersist} che impone l'ordinamento
	 * canonico prima della persistenza.
	 * </p>
	 * <p>
	 * Questo comportamento evita falsi negativi durante confronti in memoria
	 * (ad esempio nelle collezioni Java o nella logica del service layer) e
	 * impedisce che due regole speculari vengano considerate distinte prima del
	 * salvataggio nel database.
	 * </p>
	 * <p>
	 * <strong>Nota:</strong> qualsiasi modifica alla logica di questo metodo deve
	 * essere mantenuta coerente con l'implementazione di {@link #hashCode()}, in
	 * conformità al contratto definito da {@link Object#equals(Object)} e
	 * {@link Object#hashCode()}.
	 * </p>
	 * @param obj l'oggetto da confrontare con questa istanza.
	 * @return {@code true} se l'oggetto rappresenta la stessa regola di
	 * compatibilità, indipendentemente dall'ordine delle due classi ADR;
	 * {@code false} altrimenti.
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
		AdrClass thisFirst = min(adrClassA, adrClassB);
		AdrClass thisSecond = max(adrClassA, adrClassB);
		AdrClass otherFirst = min(other.adrClassA, other.adrClassB);
		AdrClass otherSecond = max(other.adrClassA, other.adrClassB);
		return Objects.equals(thisFirst, otherFirst) && Objects.equals(thisSecond, otherSecond);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("CompatibilityRule [id=").append(id).append(", isCompatible=").append(isCompatible)
			.append(", warningNote=").append(warningNote).append("]");
		return builder.toString();
	}
}
