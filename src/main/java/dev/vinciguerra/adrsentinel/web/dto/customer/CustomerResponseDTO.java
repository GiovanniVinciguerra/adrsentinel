package dev.vinciguerra.adrsentinel.web.dto.customer;

import dev.vinciguerra.adrsentinel.db.customer.Customer;
import dev.vinciguerra.adrsentinel.db.customer.CustomerSnapshot;

/**
 * Data Transfer Object (DTO) immutabile che modella il payload di risposta (Response Payload) 
 * restituito al client dalle API REST relative alla risorsa Cliente (Customer).
 * <p><b>Ruolo Architetturale (Outbound Mask):</b></p>
 * Questo record agisce come maschera di uscita per l'entità di dominio. Il suo scopo è garantire 
 * un rigido disaccoppiamento tra il layer di persistenza (Database/JPA) e la vista di presentazione 
 * (Frontend). Filtrando i campi dell'entità originale, previene l'esposizione accidentale di metadati 
 * sensibili o irrilevanti per il client (es. ID surrogati primari, timestamp di auditing, versioning) 
 * e fornisce un contratto dati stabile, invariabile e thread-safe.
 * @param companyName La ragione sociale o il nome commerciale del cliente.
 * @param vatNumber Il numero di identificazione fiscale (Partita IVA / VAT Number).
 * @param legalAddress L'indirizzo completo della sede legale.
 * @param role Il ruolo di questo customer. Attenzione che questo campo è valorizzato con un {@link CustomerRole} solo 
 * e unicamente se è un dato storico (Snapshot), altrimenti è usato il placeholder {@code "NONE"}.
 * @param active Flag booleano che indica lo stato del ciclo di vita del cliente nel sistema 
 * (es. {@code true} se operativo, {@code false} se soggetto a soft-delete o sospensione).
 * @param historicalData Flag booleano di natura architetturale che indica se il payload restituito 
 * rappresenta lo stato anagrafico corrente (vivo) del cliente oppure un reperto 
 * d'archivio cristallizzato nel tempo (snapshot di una spedizione).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record CustomerResponseDTO(String companyName, String vatNumber, String legalAddress,
		String role, Boolean active, Boolean historicalData) {
	
	/**
	 * Pattern "Static Factory Method" che converte un'entità di dominio attiva {@link Customer} 
	 * nella sua rappresentazione DTO destinata alla serializzazione verso il client.
	 * <p><b>Dettaglio delle conversioni applicate (Entity -> DTO):</b></p>
	 * <ul>
	 * <li><b>Safe Null-Check:</b> Se l'entità in ingresso è {@code null}, il metodo intercetta 
	 * la condizione e restituisce {@code null} in modo sicuro, garantendo la robustezza contro 
	 * eventuali {@code NullPointerException} durante il mapping di proiezioni opzionali.</li>
	 * <li><b>Proiezione Diretta:</b> I dati anagrafici primari (Ragione Sociale, P.IVA, Indirizzo) 
	 * e lo stato di attività vengono mappati in rapporto 1:1 verso i campi immutabili del record.</li>
	 * <li><b>Business Logic Implicita:</b> Il campo {@code historicalData} viene iniettato forzatamente 
	 * a {@code false}. Poiché questo specifico metodo consuma un'entità master {@link Customer}, il sistema 
	 * segnala inequivocabilmente al client che i dati forniti rappresentano la "Source of Truth" attuale 
	 * e modificabile del cliente, non un artefatto storico.</li>
	 * </ul>
	 * @param entity L'entità di dominio {@link Customer} estratta dal layer di persistenza.
	 * @return Un'istanza popolata di {@link CustomerResponseDTO}, o {@code null} se l'entità fornita è nulla.
	 */
	public static CustomerResponseDTO fromEntity(Customer entity) {
		if(entity == null)
			return null;
		
		return new CustomerResponseDTO(
			entity.getCompanyName(),
			entity.getVatNumber(),
			entity.getLegalAddress(),
			"NONE",
			entity.isActive(),
			false
		);
	}
	
	/**
	 * Pattern "Static Factory Method" in overloading che converte un'entità storica
	 * {@link CustomerSnapshot} nella sua rappresentazione DTO unificata destinata al client.
	 * <p><b>Dettaglio delle conversioni applicate (Snapshot -> DTO):</b></p>
	 * <ul>
	 * <li><b>Safe Null-Check:</b> Se l'entità in ingresso è {@code null}, il metodo restituisce {@code null}
	 * in modo sicuro, garantendo consistenza architetturale e prevenendo eccezioni a runtime durante il fetch di relazioni opzionali.</li>
	 * <li><b>Proiezione Storica:</b> I dati anagrafici primari (Ragione Sociale, Partita IVA, Indirizzo Legale)
	 * vengono estratti dalla loro controparte cristallizzata (campi "Snap"), generata nell'esatto istante di partenza della spedizione.</li>
	 * <li><b>De-tipizzazione Enum:</b> Il ruolo operativo associato al cliente ({@code roleSnap}) viene de-serializzato
	 * in stringa primitiva richiamando il metodo {@code name()}, garantendo la compatibilità JSON e preservando la nomenclatura esatta.</li>
	 * <li><b>Soppressione Flag di Ciclo di Vita:</b> Poiché lo snapshot rappresenta un "manifest" di viaggio congelato
	 * nel tempo, lo stato di attività corrente dell'azienda ({@code active}) perde di validità contestuale. Viene pertanto
	 * forzatamente omesso (impostato a {@code null}) per non trasmettere al frontend un'informazione di stato fuorviante.</li>
	 * <li><b>Business Logic Implicita:</b> Il campo {@code historicalData} viene forzato rigidamente a {@code true}.
	 * Questo marker architetturale segnala inequivocabilmente al client che i dati forniti rappresentano un reperto
	 * d'archivio in sola lettura (Read-Only) e non l'anagrafica "viva" presente a sistema.</li>
	 * </ul>
	 * @param entity L'entità storica {@link CustomerSnapshot} estratta dal layer di persistenza, rappresentante
	 * la fotografia anagrafica dell'azienda nell'istante in cui la spedizione ha abbandonato lo stato PLANNED.
	 * @return Un'istanza popolata di {@link CustomerResponseDTO}, o {@code null} se l'entità fornita è nulla.
	 */
	public static CustomerResponseDTO fromEntity(CustomerSnapshot entity) {
		if(entity == null)
			return null;
		
		return new CustomerResponseDTO(
			entity.getCompanyNameSnap(),
			entity.getVatNumberSnap(),
			entity.getLegalAddressSnap(),
			entity.getRoleSnap().name(),
			null,
			true
		);
	}
}
