package dev.vinciguerra.adrsentinel.web.dto.waybill;

import dev.vinciguerra.adrsentinel.db.waybill.Waybill;

/**
 * Data Transfer Object (DTO) immutabile deputato alla serializzazione in uscita (Response Payload) 
 * dei metadati informativi di un Documento di Trasporto (D.D.T.).
 * <p><b>Contesto Architetturale (Data Hiding &amp; Network Optimization):</b></p>
 * Questo {@code record} funge da strato di presentazione leggero per l'entità di dominio {@code Waybill}. 
 * La sua caratteristica architetturale primaria è l'omissione deliberata del payload binario 
 * (il file PDF). Questa segregazione permette di interrogare le API REST per ottenere elenchi, 
 * storici o dettagli anagrafici dei documenti senza scatenare gravosi trasferimenti di rete 
 * o picchi di consumo della RAM, delegando il download del file a endpoint specializzati.
 * @param ddtNumber L'identificativo di business univoco del documento (es. "DDT-TRK-88492011A").
 * @param filename Il nome fisico del file, utile al client frontend per impostare l'attributo 
 * di download nativo del browser.
 * @param contentType Il MIME type del documento (tipicamente "application/pdf"), necessario per 
 * istruire correttamente i visualizzatori documentali lato client.
 * @param createdAt La data di emissione del documento, serializzata nativamente in formato 
 * stringa (ISO-8601) per garantire una perfetta compatibilità cross-platform 
 * durante il parsing JSON.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0 
 */
public record WaybillResponseDTO(String ddtNumber, String filename, String contentType, String createdAt) {
	/**
	 * Costruttore statico (Static Factory Method) per la mappatura sicura dall'entità 
	 * persistente al DTO di risposta.
	 * <p><b>Pipeline di Trasformazione:</b></p>
	 * <ul>
	 * <li><i>Null-Safety:</i> Intercetta proattivamente entità nulle, restituendo {@code null} 
	 * in modo silente per prevenire le classiche {@code NullPointerException} a cascata 
	 * durante la mappatura delle collection.</li>
	 * <li><i>Decoupling:</i> Isola completamente la logica di presentazione dalle dipendenze 
	 * interne di Hibernate, "appiattendo" (flattening) i tipi complessi come {@code LocalDate} 
	 * nella loro equivalente rappresentazione testuale sicura per il web.</li>
	 * </ul>
	 * @param entity L'entità di dominio {@code Waybill} recuperata dallo strato di persistenza.
	 * @return Una nuova istanza immutabile del DTO popolata con i metadati del documento, 
	 * oppure {@code null} se l'entità sorgente risulta assente.
	 */
	public static WaybillResponseDTO fromEntity(Waybill entity) {
		if(entity == null)
			return null;
		
		return new WaybillResponseDTO(
			entity.getDdtNumber(),
			entity.getFilename(),
			entity.getContentType(),
			entity.getCreatedAt().toString());
	}
}
