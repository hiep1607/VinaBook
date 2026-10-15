package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.fs.entity.*;

class BookRelevanceScorerTest {
    final BookRelevanceScorer scorer=new BookRelevanceScorer(new ChatIntentService());
    Product book(){Product p=new Product();p.setName("");return p;}
    BookProfile profile(){BookProfile p=new BookProfile();p.setApproved(true);return p;}
    @Test void subjectMatchOutweighsIncidentalAudienceWords() {
        double subject=scorer.score("ký ức tuổi thơ người lớn",book(),profile(),List.of("Chủ đề: ký ức tuổi thơ","Thể loại: truyện tuổi thơ"));
        double incidental=scorer.score("ký ức tuổi thơ người lớn",book(),profile(),List.of("Độc giả: trẻ em và người lớn","Chủ đề: tình bạn"));
        assertThat(subject).isGreaterThan(incidental);
    }
    @Test void completePhraseOutweighsScatteredWords() {
        double phrase=scorer.score("phát triển sản phẩm",book(),profile(),List.of("Chủ đề: phát triển sản phẩm"));
        double scattered=scorer.score("phát triển sản phẩm",book(),profile(),List.of("Chủ đề: sản phẩm và sự phát triển"));
        assertThat(phrase).isGreaterThan(scattered);
    }
    @Test void repeatedLabelsCannotAccumulateWithinTheSameDimension() {
        List<String> once=List.of("Chủ đề: thiên kiến nhận thức");
        List<String> repeated=List.of("Chủ đề: thiên kiến nhận thức","Chủ đề: thiên kiến nhận thức / cognitive bias","Chủ đề: nhận thức và thiên kiến");
        assertThat(scorer.score("thiên kiến nhận thức",book(),profile(),repeated))
            .isEqualTo(scorer.score("thiên kiến nhận thức",book(),profile(),once));
    }
    @Test void draftAndMissingProfilesCannotSupplyLabelEvidence() {
        BookProfile draft=profile();draft.setApproved(false);
        assertThat(scorer.score("thiên kiến nhận thức",book(),draft,List.of("Chủ đề: thiên kiến nhận thức"))).isZero();
        assertThat(scorer.score("thiên kiến nhận thức",book(),null,List.of("Chủ đề: thiên kiến nhận thức"))).isZero();
    }
    @Test void legacyAndUnknownHeadingsDoNotGainEditorialWeight() {
        assertThat(scorer.score("thiên kiến nhận thức",book(),profile(),List.of("thiên kiến nhận thức","Không rõ: thiên kiến nhận thức"))).isZero();
    }
    @Test void aliasesWorkWithoutStackingTheirScores() {
        assertThat(scorer.score("unit testing",book(),profile(),List.of("Chủ đề: kiểm thử đơn vị / unit testing")))
            .isEqualTo(scorer.score("unit testing",book(),profile(),List.of("Chủ đề: unit testing"))).isPositive();
    }
    @Test void coveringAnExplicitQualifierOutweighsManyRepeatedPartialMatches() {
        BookProfile painful=profile();painful.setSummary("Truyện tuổi thơ có nỗi đau.");
        BookProfile general=profile();general.setSummary("Truyện tuổi thơ.");
        double complete=scorer.score("tuổi thơ nỗi đau",book(),painful,List.of("Thể loại: truyện tuổi thơ","Cách trình bày: có nỗi đau"));
        double partial=scorer.score("tuổi thơ nỗi đau",book(),general,List.of("Thể loại: truyện tuổi thơ","Chủ đề: tuổi thơ","Mục đích: đọc tuổi thơ","Độc giả: thích tuổi thơ"));
        assertThat(complete).isGreaterThan(partial);
    }
}
