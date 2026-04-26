package dev.vinciguerra.adrsentinel.db.adrclass;

import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entità JPA che rappresenta una Classe di Pericolo secondo la normativa internazionale ADR.
 * <p>
 * Questa anagrafica definisce le macro-categorie di rischio (es. Classe 3 per Liquidi Infiammabili, 
 * Classe 6.1 per Materie Tossiche) a cui ogni Numero ONU ({@code OnuNumber}) deve obbligatoriamente appartenere.
 * </p>
 * <h3>Design Architetturale:</h3>
 * <ul>
 * <li><b>Business Key Naturale:</b> Il codice della classe ({@code classCode}) funge da chiave naturale univoca, 
 * garantita a livello di database tramite un {@code @UniqueConstraint}. Questo disaccoppia la logica di business 
 * dalla chiave surrogata ({@code id}).</li>
 * <li><b>Tolerant Reader Pattern:</b> L'entità implementa una normalizzazione silente ({@link #normalize()}) 
 * per assorbire e correggere automaticamente input utente "sporchi" (es. copia-incolla dai PDF normativi), 
 * migliorando drasticamente la User Experience senza sacrificare la validazione dei dati.</li>
 * <li><b>Validazione Dichiarativa Estrema:</b> L'integrità del formato del codice classe è delegata a una 
 * Regex con Lookahead, capace di validare complesse regole tipografiche prescritte dal manuale ONU.</li>
 * </ul>
 *
 * @author Giovanni Vinciguerra
 * @version 2.0 (Refactored with Jakarta Validation & UX Normalization)
 * @since 1.0
 */
@Entity
@Table(
	name = "adr_class",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_class_code", columnNames = {"class_code"})
	}
)
public class AdrClass implements Comparable<AdrClass> {
	/** Chiave primaria surrogata autogenerata. */
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	/**
	 * L'identificativo principale della Classe ADR (Label).
	 * <p>
	 * <b>Vincoli Architetturali e di Dominio:</b>
	 * <ul>
	 * <li>La stringa non può essere vuota.</li>
	 * <li>La stringa deve essere lunga al massimo 4 caratteri.</li>
	 * <li>Deve iniziare rigorosamente con una singola cifra numerica (es. "3", "6").</li>
	 * <li>Può contenere un solo punto separatore seguito da numeri (es. "4.1").</li>
	 * <li>Può terminare con una singola lettera opzionale.</li>
	 * </ul>
	 * Questo campo è la vera Business Key dell'entità.
	 * </p>
	 */
	@NotBlank(message = "Adr class code cannot be empty or blank")
	@Pattern(
		regexp = "^(?=.{1,4}$)\\d(\\.\\d+)?[a-zA-Z]?$",
		message = "Invalid: max 4 chars. Format: 1 digit + optional '.numbers' + optional letter."
	)
	@Column(
		name = "class_code",
		nullable = false,
		length = 4
	)
	private String classCode;
	/**
	 * La descrizione ufficiale della classe di pericolo (es. "Materie tossiche").
	 * <p>
	 * <b>Nota sulla Validazione:</b> Non ammette valori nulli, vuoti o composti da soli spazi.
	 * La lunghezza minima di 3 caratteri accoglie la classe con la descrizione più corta in assoluto ("Gas").
	 * I tentativi di inserire ritorni a capo (\n, \r) o tabulazioni (\t) verranno intercettati e corretti 
	 * in fase di normalizzazione prima di arrivare al validatore.
	 * </p>
	 */
	@NotBlank(message = "Description cannot be empty or blank")
	@Size(min = 3, max = 255, message = "Class description must be between 3 and 255 characters")
	@Column(
		name = "description",
		nullable = false,
		length = 255
	)
	private String description;
	
	/**
	 * Metodo di normalizzazione del ciclo di vita (Lifecycle Callback).
	 * <p>
	 * Viene invocato automaticamente dal framework JPA (Hibernate) prima delle operazioni di 
	 * INSERT, UPDATE e subito dopo il ricaricamento in memoria (LOAD).
	 * </p>
	 * <b>Operazioni eseguite:</b>
	 * <ul>
	 * <li><b>classCode:</b> Rimuove gli spazi laterali e forza i caratteri in maiuscolo (es. "1.4s" -> "1.4S").</li>
	 * <li><b>description:</b> Applica una pulizia profonda per supportare il "Copia-Incolla".
	 * Sostituisce ritorni a capo e tabulazioni con spazi, collassa gli spazi multipli in uno singolo 
	 * e rimuove gli spazi in eccesso ai bordi.</li>
	 * </ul>
	 */
	@PrePersist
	@PreUpdate
	@PostLoad
	private void normalize() {
		if(classCode != null)
			classCode = classCode.trim().toUpperCase();
		if(description != null) {
			description = description.replaceAll("[\\r\\n\\t]+", " ");
			description = description.replaceAll(" {2,}", " ");
			description = description.trim();
		}
	}
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getClassCode() {
		return classCode;
	}
	
	public void setClassCode(String classCode) {
		this.classCode = classCode;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
     * Calcola l'hash code dell'entità basandosi esclusivamente sulla Business Key ({@code classCode}).
     * Questo garantisce la stabilità dell'hash in collezioni come gli {@code HashSet} indipendentemente 
     * dallo stato di persistenza (ID null).
     *
     * @return L'hash code basato sul codice della classe.
     */
	@Override
	public int hashCode() {
		return Objects.hash(classCode);
	}
	
	/**
     * Verifica l'uguaglianza tra due entità AdrClass.
     * L'uguaglianza è considerata "forte" ed è basata unicamente sul {@code classCode}.
     * Cruciale per la corretta invalidazione e rimozione delle entità dai layer di Cache (es. {@code temp.remove(value)}).
     *
     * @param obj L'oggetto da confrontare.
     * @return true se gli oggetti hanno lo stesso classCode, false altrimenti.
     */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AdrClass other = (AdrClass) obj;
		return Objects.equals(classCode, other.classCode);
	}
	
	/**
     * Compara questa entità con un'altra in base all'ordine alfabetico/numerico del {@code classCode}.
     * Permette l'ordinamento naturale nelle collezioni (es. {@code TreeSet}).
     *
     * @param classB L'entità con cui effettuare il confronto.
     * @return un valore negativo, zero o positivo se questo classCode è minore, uguale o maggiore di quello passato.
     */
	@Override
	public int compareTo(AdrClass classB) {
		return classCode.compareTo(classB.classCode);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("AdrClass [id=").append(id).append(", classCode=").append(classCode).append(", description=")
			.append(description).append("]");
		return builder.toString();
	}
}
