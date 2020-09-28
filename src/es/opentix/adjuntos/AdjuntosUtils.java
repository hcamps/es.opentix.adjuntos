package es.opentix.adjuntos;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.ConfigParameters;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBConfigFileProvider;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.attachment.CoreAttachImplementation;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.UtilSql;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.database.SessionInfo;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.ad.utility.FileType;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.service.db.QueryTimeOutUtil;

import es.opentix.devpro.OpxLog;

/**
 * Code develped by Opentix S.L.
 * 
 * @author vforner
 * @created 9 feb. 2017
 */
public class AdjuntosUtils {

    public static Attachment anyadirAdjunto(String idTabla, String idRegistro, File fichero, String formato, String nombre, String descripcion,
            Object claseQueLlama) {

        try {
            OBContext.setAdminMode(true);
            OBCriteria<FileType> tipoFicheroCriteria = OBDal.getInstance().createCriteria(FileType.class);
            tipoFicheroCriteria.add(Restrictions.eq(FileType.PROPERTY_NAME, formato));
            FileType tipoFichero = tipoFicheroCriteria.list().isEmpty() ? null : tipoFicheroCriteria.list().get(0);

            return anyadirAdjunto(idTabla, idRegistro, fichero, tipoFichero, nombre, descripcion, claseQueLlama);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    public static Attachment anyadirAdjunto(String idTabla, String idRegistro, File fichero, FileType filetype, String nombre, String descripcion,
            Object claseQueLlama) {

        OBContext.getOBContext();
        OBContext.setAdminMode();

        try {

            String tipoFichero = filetype == null ? null : filetype.getId();
            Table tabla = OBDal.getInstance().get(Table.class, idTabla);

            Long secuencia = recogerUltimaSecuenciaAdjuntos(idTabla, idRegistro, claseQueLlama);

            Attachment adjunto = crearAdjunto(tabla, idRegistro, nombre, descripcion, tipoFichero, secuencia);

            try {

                String directorioEspecifico = CoreAttachImplementation.getAttachmentDirectoryForNewAttachments(idTabla, idRegistro);
                String directorioAttachments = new ConfigParameters(OBConfigFileProvider.getInstance().getServletContext()).strFTPDirectory;
                String separadorDirectorio = System.getProperty("file.separator");

                File directorioFinal = new File(directorioAttachments + separadorDirectorio + directorioEspecifico);

                if (!directorioFinal.exists()) directorioFinal.mkdirs();

                adjunto.setPath(directorioEspecifico);

                final File ficheroAdjunto = new File(directorioFinal, nombre);

                FileUtils.copyFile(fichero, ficheroAdjunto);
                OBDal.getInstance().save(adjunto);

            } catch (IOException e) {
                e.printStackTrace();
            }

            return adjunto;
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    public static Attachment crearAdjunto(Table tabla, String idRegistro, String nombre, String descripcion, String tipoFichero, Long secuencia) {

        OBCriteria<Attachment> criteria = OBDal.getInstance().createCriteria(Attachment.class);
        criteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, idRegistro));
        criteria.add(Restrictions.eq(Attachment.PROPERTY_NAME, nombre));

        Attachment attachment = null;
        if (criteria.list().isEmpty()) {
            attachment = OBProvider.getInstance().get(Attachment.class);
        } else {
            attachment = criteria.list().get(0);
        }
        attachment.setName(nombre);
        attachment.setText(descripcion);
        attachment.setTable(tabla);
        attachment.setRecord(idRegistro);
        attachment.setDataType(tipoFichero);
        attachment.setSequenceNumber(secuencia);

        return attachment;
    }

    public static File crearFicheroImagen(String baseImagen, Object claseQueLlama) {

        byte[] imagedata = DatatypeConverter.parseBase64Binary(baseImagen.substring(baseImagen.indexOf(",") + 1));
        BufferedImage bufferedImage = null;
        try {
            bufferedImage = ImageIO.read(new ByteArrayInputStream(imagedata));
        } catch (IOException e) {
            OpxLog.exception(claseQueLlama, e);
        }

        File directorioPrueba = new File("imagenApp");

        //OpxLog.error(directorioPrueba.getAbsolutePath());

        File directorio = new File("/opt/OpenbravoERP/imagenApp");

        if (!directorio.exists()) {
            directorio.mkdirs();
        }

        File fichero = new File("/opt/OpenbravoERP/imagenApp/image.png");

        //        OpxLog.error(fichero, fichero.getAbsolutePath());
        //        System.out.println("Buffered");
        //        System.out.println(baseImagen);
        //        System.out.println(fichero.getAbsolutePath());
        //        System.out.println(fichero.getPath());
        try {
            ImageIO.write(bufferedImage, "png", fichero);
        } catch (IOException e) {
            OpxLog.exception(claseQueLlama, e);
        }
        return fichero;
    }

    public static Long recogerUltimaSecuenciaAdjuntos(String idTabla, String idRegistro, Object claseQueLlama) {

        Long numSecuencia = 10L;

        try {
            String sql = "select coalesce(max(seqno), 0) as maximo from c_file where ad_table_id = '" + idTabla + "' and ad_record_id = '"
                    + idRegistro + "'";
            ConnectionProvider conexion = new DalConnectionProvider(true);
            PreparedStatement st = conexion.getPreparedStatement(sql);

            QueryTimeOutUtil.getInstance().setQueryTimeOut(st, SessionInfo.getQueryProfile());

            ResultSet resultado = st.executeQuery();
            if (resultado.next()) {
                String strReturn = UtilSql.getValue(resultado, "maximo");
                try {
                    numSecuencia = Long.parseLong(strReturn);
                    numSecuencia += 10L;
                } catch (Exception e2) {
                    OpxLog.exception(claseQueLlama, e2);
                }
            }
            resultado.close();
        } catch (Exception e) {
            OpxLog.exception(claseQueLlama, e);
        }

        return numSecuencia;
    }

    public static JSONArray recogerAdjuntos(String idRegistro) {

        JSONArray adjuntosArray = new JSONArray();

        try {
            OBContext.getOBContext();
            OBContext.setAdminMode();

            OBCriteria<Attachment> adjuntoCriteria = OBDal.getInstance().createCriteria(Attachment.class);
            adjuntoCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, idRegistro));

            String directorioAttachments = new ConfigParameters(OBConfigFileProvider.getInstance().getServletContext()).strFTPDirectory;
            String separadorDirectorio = System.getProperty("file.separator");

            for (Attachment adjunto : adjuntoCriteria.list()) {

                String ruta = directorioAttachments + separadorDirectorio + adjunto.getPath() + separadorDirectorio + adjunto.getName();
                File fichero = new File(ruta);
                String ficheroLeido = leerFichero(fichero);

                JSONObject adjuntoJSON = new JSONObject();
                adjuntoJSON.put("nombre", adjunto.getName());
                adjuntoJSON.put("fichero", ficheroLeido);

                adjuntosArray.put(adjuntoJSON);

            }

        } catch (Exception e) {
            OBDal.getInstance().rollbackAndClose();
            throw new OBException(e.getMessage());
        } finally {
            OBContext.getOBContext();
            OBContext.restorePreviousMode();
        }

        return adjuntosArray;
    }

    public static String recogerUnicoAdjunto(String idRegistro) {

        String ficheroLeido = "";

        try {
            OBContext.getOBContext();
            OBContext.setAdminMode();

            OBCriteria<Attachment> adjuntoCriteria = OBDal.getInstance().createCriteria(Attachment.class);
            adjuntoCriteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, idRegistro));
            adjuntoCriteria.setMaxResults(1);

            Attachment adjunto = (Attachment) adjuntoCriteria.uniqueResult();

            if (adjunto != null) {
                String directorioAttachments = new ConfigParameters(OBConfigFileProvider.getInstance().getServletContext()).strFTPDirectory;
                String separadorDirectorio = System.getProperty("file.separator");

                String ruta = directorioAttachments + separadorDirectorio + adjunto.getPath() + separadorDirectorio + adjunto.getName();
                File fichero = new File(ruta);
                ficheroLeido = leerFichero(fichero);

            }

        } catch (Exception e) {
            OBDal.getInstance().rollbackAndClose();
            throw new OBException(e.getMessage());
        } finally {
            OBContext.getOBContext();
            OBContext.restorePreviousMode();
        }

        return ficheroLeido;
    }

    public static String leerFichero(File fichero) {

        try {
            InputStream is = new FileInputStream(fichero);

            long tamanyo = fichero.length();

            if (tamanyo > Integer.MAX_VALUE) {
                return "";
            }

            byte[] bytes = new byte[(int) tamanyo];

            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }

            if (offset < bytes.length) {
                throw new OBException("Could not completely read file " + fichero.getName());
            }

            is.close();

            byte[] encoded = Base64.encodeBase64(bytes);
            String encodedString = new String(encoded);

            return encodedString;

        } catch (IOException e) {
            throw new OBException(e.getMessage());
        }

    }
}
