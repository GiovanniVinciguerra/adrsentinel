package dev.vinciguerra.adrsentinel.db.onunumber;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;

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
	 * Esegue un lookup puntuale (1-to-1) basato sulla chiave di business naturale: il Codice ONU.
	 * <p>
	 * <b>Sicurezza e Gestione Null:</b><br>
	 * Restituisce un {@link Optional} per forzare contrattualmente chi chiama il metodo 
	 * (il Service Layer) a gestire esplicitamente lo scenario in cui l'operatore inserisce 
	 * un codice di 4 cifre inesistente, garantendo solidità ed evitando eccezioni non gestite a runtime.
	 * </p>
	 * @param onuCode il codice a 4 cifre identificativo della materia (es. "1203" per la benzina).
	 * @return un contenitore {@link Optional} con l'entità se presente nel DB, altrimenti vuoto.
	 */
	Optional<OnuNumber> findByOnuCode(String onuCode);
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
	 * Estrae l'intera libreria di materie pericolose appartenenti a una specifica Macro-Classe ADR.
	 * <p>
	 * <b>Ottimizzazione Relazionale:</b><br>
	 * Accettando l'entità {@link AdrClass} come parametro, il framework delega al database 
	 * relazionale l'esecuzione di una ricerca ultra-rapida basata sull'indice della Foreign Key.
	 * </p>
	 * <p>
	 * <b>Use Case Logistico:</b><br>
	 * Metodo essenziale per alimentare i menu a tendina "a cascata" (Cascading Dropdowns) 
	 * sul frontend React: non appena l'utente seleziona ad esempio "Classe 3", questo metodo 
	 * fornisce immediatamente la lista di tutte e sole le materie infiammabili selezionabili.
	 * </p>
	 * @param adrClass l'istanza della classe ADR (chiave esterna) da filtrare.
	 * @return la collezione completa delle merci appartenenti a quella specifica categoria di pericolo.
	 */
	List<OnuNumber> findByAdrClass(AdrClass adrClass);
}
