package dev.vinciguerra.adrsentinel.db.onunumber;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Livello di accesso ai dati (Data Access Layer) per il catalogo delle merci pericolose ADR (Numeri ONU).
 * <p>
 * <b>Ruolo Architetturale:</b><br>
 * Questo repository gestisce i "Dati di Catalogo" (Reference Data) dell'applicazione. 
 * A differenza delle entità transazionali (come le Spedizioni), la tabella sottostante 
 * contiene un numero finito, chiuso e quasi immutabile di record (circa 3.500 voci, 
 * aggiornate solo in occasione della revisione biennale del manuale ADR).
 * </p>
 * <p>
 * <b>Strategia di Lettura (Read-Heavy & Bounded Data):</b><br>
 * Poiché il dominio è strettamente limitato, i metodi di estrazione di massa restituiscono 
 * volutamente intere collezioni ({@link List}) anziché blocchi impaginati. L'intero strato 
 * è progettato per essere intercettato dalla L1 Cache (Caffeine) a livello di Service, 
 * al fine di azzerare il carico sul database e sulla rete durante l'operatività quotidiana.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface OnuNumberRepository extends JpaRepository<OnuNumber, Long> {
	/**
	 * Ricerca e restituisce tutte le istanze e le varianti di Numeri ONU associate a uno specifico codice a 4 cifre.
	 * <p>
	 * <b>Dinamica del Dominio (Design della Cardinalità):</b><br>
	 * Nel contesto normativo ADR, la relazione tra un "Codice ONU" testuale (es. "1993") e la sua definizione 
	 * non è strettamente biunivoca (1:1). Uno stesso codice può generare molteplici record (varianti) distinti 
	 * per Gruppo di Imballaggio (Packing Group I, II, III), Codice di Classificazione o Disposizioni Speciali. 
	 * Questa firma di metodo modella correttamente questa peculiarità del dominio restituendo una {@link List} 
	 * in luogo di un singolo oggetto, permettendo al chiamante di gestire tutte le casistiche applicabili.
	 * </p>
	 * <p>
	 * <b>Meccanica della Query (Query Method Derivation):</b><br>
	 * Il framework analizza la direttiva lessicale {@code findBy} seguita dall'esatto nome della proprietà 
	 * {@code onuCode} dell'entità {@link OnuNumber}. Durante il bootstrap dell'applicazione, Spring Data JPA 
	 * genera dinamicamente e in automatico la query JPQL e SQL sottostante 
	 * (es. {@code SELECT o FROM OnuNumber o WHERE o.onuCode = ?1}).
	 * </p>
	 * @param onuCode La Business Key, ovvero la stringa a 4 cifre (tipicamente) che identifica la materia 
	 * pericolosa (es. "1203" per benzina, "0027" per polvere nera). Il framework mappa 
	 * questo parametro su un {@code PreparedStatement} SQL nativo, garantendo la totale 
	 * immunità da attacchi di tipo SQL Injection.
	 * @return Una {@link List} di entità {@link OnuNumber} che soddisfano il criterio di ricerca. 
	 * <br><b>Null-Safety:</b> Se il database non contiene alcun record associato al codice fornito, 
	 * il metodo restituisce una lista vuota in modo sicuro (Empty List), <b>mai</b> {@code null}.
	 */
	List<OnuNumber> findByOnuCode(String onuCode);
	/**
	 * Raggruppa l'intero sottoinsieme del catalogo ADR associato a un determinato profilo di rischio.
	 * <p>
	 * <b>Capacity Planning:</b><br>
	 * Un singolo Codice Kemler (es. "33") è condiviso al massimo da un centinaio di Numeri ONU. 
	 * Il risultato viene estratto integralmente in RAM (latenza O(1) con cache attiva) per poi 
	 * essere compresso (GZIP) e inviato al client in una singola, rapida chiamata di rete.
	 * </p>
	 * @param kemlerCode il Numero di Identificazione del Pericolo (es. "33", "80", "X333").
	 * @return la lista finita e chiusa di tutti i Numeri ONU che presentano tale rischio.
	 */
	List<OnuNumber> findByKemlerCode(String kemlerCode);
	/**
	 * Esegue il recupero massivo di tutti i Numeri ONU associati a una specifica Classe di Pericolo ADR, 
	 * delegando la generazione della query SQL al motore interno di Spring Data JPA.
	 * <p>
	 * <b>Meccanica della Query (Property Traversal):</b><br>
	 * La firma di questo metodo sfrutta la sintassi avanzata di navigazione delle proprietà. 
	 * L'utilizzo dell'underscore ({@code _}) funge da delimitatore esplicito per il parser del framework:
	 * <ol>
	 * <li>Istruisce Spring Data a localizzare la proprietà relazionale {@code adrClass} 
	 * (tipicamente mappata come {@code @ManyToOne} all'interno dell'entità {@code OnuNumber}).</li>
	 * <li>"Attraversa" l'oggetto relazionato e punta direttamente alla sua proprietà interna {@code classCode}.</li>
	 * </ol>
	 * Questa sintassi previene in modo assoluto le ambiguità di parsing (evitando che Spring cerchi 
	 * per errore un campo inesistente chiamato "adrClassClassCode" sull'entità principale).
	 * </p>
	 * <p>
	 * <b>Ottimizzazione Relazionale Sottostante:</b><br>
	 * A runtime, Hibernate traduce questa firma logica in una query SQL fisica che esegue automaticamente 
	 * una {@code INNER JOIN} tra la tabella dei numeri ONU e quella delle classi ADR, permettendo un 
	 * filtraggio diretto ed efficiente lato database senza caricare in memoria dati non necessari.
	 * </p>
	 * @param adrClassCode Il codice identificativo alfanumerico (Business Key) della classe ADR 
	 * (es. "3", "8", "6.1").
	 * @return Una {@link List} contenente le entità {@link OnuNumber} collegate alla classe ADR indicata. 
	 * <br><b>Null-Safety:</b> In conformità ai contratti di Spring Data JPA, qualora nessun record 
	 * soddisfi il criterio di ricerca, il metodo garantirà la restituzione di una lista vuota (Empty List) 
	 * e <b>mai</b> di un valore {@code null}, prevenendo {@code NullPointerException} a valle.
	 */
	List<OnuNumber> findByAdrClass_classCode(String adrClassCode);
}
