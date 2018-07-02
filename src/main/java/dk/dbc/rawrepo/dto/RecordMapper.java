package dk.dbc.rawrepo.dto;

import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.Field;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;

import java.util.ArrayList;
import java.util.List;

public class RecordMapper {

    public static RecordMetaDataDTO recordMetaDataToDTO(Record record) {
        RecordMetaDataDTO dto = new RecordMetaDataDTO();
        dto.setRecordId(recordIdToDTO(record.getId()));
        dto.setDeleted(record.isDeleted());
        dto.setCreated(record.getCreated().toString());
        dto.setModified(record.getModified().toString());
        dto.setMimetype(record.getMimeType());
        dto.setTrackingId(record.getTrackingId());

        return dto;
    }

    public static RecordDTO recordToDTO(Record rawRecord, MarcRecord marcRecord) {
        RecordDTO dto = new RecordDTO();
        dto.setRecordId(recordIdToDTO(rawRecord.getId()));
        dto.setDeleted(rawRecord.isDeleted());
        dto.setCreated(rawRecord.getCreated().toString());
        dto.setModified(rawRecord.getModified().toString());
        dto.setMimetype(rawRecord.getMimeType());
        dto.setTrackingId(rawRecord.getTrackingId());
        dto.setContent(rawRecord.getContent());
        dto.setContentJSON(contentToDTO(marcRecord));

        return dto;
    }

    public static RecordIdDTO recordIdToDTO(RecordId recordId) {
        RecordIdDTO dto = new RecordIdDTO();
        dto.setBibliographicRecordId(recordId.getBibliographicRecordId());
        dto.setAgencyId(recordId.getAgencyId());

        return dto;
    }

    public static ContentDTO contentToDTO(MarcRecord marcRecord) {
        ContentDTO dto = new ContentDTO();

        dto.setLeader(marcRecord.getLeader().getData());

        List<FieldDTO> fieldDTOList = new ArrayList<>();
        for (Field field : marcRecord.getFields()) {
            if (field instanceof DataField) {
                DataField dataField = (DataField) field;
                FieldDTO fieldDTO = new FieldDTO();
                fieldDTO.setName(dataField.getTag());
                fieldDTO.setIndicators("" + dataField.getInd1() + dataField.getInd2() + dataField.getInd2());

                List<SubfieldDTO> subfieldDTOList = new ArrayList<>();

                for (SubField subField : dataField.getSubfields()) {
                    SubfieldDTO subfieldDTO = new SubfieldDTO();
                    subfieldDTO.setName("" + subField.getCode());
                    subfieldDTO.setValue(subField.getData());

                    subfieldDTOList.add(subfieldDTO);
                }

                fieldDTO.setSubfields(subfieldDTOList);

                fieldDTOList.add(fieldDTO);
            }

            // TODO Implement other Field types
        }

        dto.setFields(fieldDTOList);

        return dto;
    }
}
