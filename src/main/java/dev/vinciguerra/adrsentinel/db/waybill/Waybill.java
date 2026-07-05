package dev.vinciguerra.adrsentinel.db.waybill;

import java.time.LocalDateTime;
import java.util.Arrays;
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

@Entity
@Table(
	name = "waybill",
	uniqueConstraints = @UniqueConstraint(name = "uk_file_name", columnNames = {"filename"})
)
public class Waybill {
	/** Chiave primaria surrogata autogenerata. */
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(
		name = "filename",
		nullable = false,
		updatable = false,
		length = 255
	)
	private String filename;
	@Column(
		name = "content_type",
		nullable = false,
		updatable = false,
		length = 255
	)
	private String contentType;
	@Lob
    @Column(
    	name = "pdf_data",
    	nullable = false,
    	updatable = false,
    	columnDefinition = "LONGBLOB"
    )
	private byte[] pdfData;
	@Column(
		name = "created_at",
		nullable = false,
		updatable = false
	)
	private LocalDateTime createdAt = LocalDateTime.now();
	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(
	    name = "shipment_id",
	    nullable = false,
	    updatable = false,
	    unique = true,
	    foreignKey = @ForeignKey(name = "fk_waybill_shipment")
	)
	private Shipment shipment;
	
	public Waybill(String filename, String contentType, byte[] pdfData, LocalDateTime createdAt,
			Shipment shipment) {
		super();
		this.filename = filename;
		this.contentType = contentType;
		this.pdfData = pdfData;
		this.createdAt = createdAt;
		this.shipment = shipment;
	}

	public Long getId() {
		return id;
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public Shipment getShipment() {
		return shipment;
	}

	@Override
	public int hashCode() {
		return Objects.hash(filename);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Waybill other = (Waybill) obj;
		return Objects.equals(filename, other.filename);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Waybill [id=").append(id).append(", filename=").append(filename).append(", contentType=")
			.append(contentType).append(", pdfData=").append(Arrays.toString(pdfData)).append(", createdAt=")
			.append(createdAt).append("]");
		return builder.toString();
	}
}
