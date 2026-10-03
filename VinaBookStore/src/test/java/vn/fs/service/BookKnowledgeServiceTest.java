package vn.fs.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.fs.entity.*;
import vn.fs.repository.*;

class BookKnowledgeServiceTest {
    ProductRepository products=mock(ProductRepository.class);
    BookProfileRepository profiles=mock(BookProfileRepository.class);
    BookTopicRepository topics=mock(BookTopicRepository.class);
    BookTopicLinkRepository links=mock(BookTopicLinkRepository.class);
    BookRelationRepository relations=mock(BookRelationRepository.class);
    BookEmbeddingGateway embeddings=mock(BookEmbeddingGateway.class);
    AiGateway ai=mock(AiGateway.class);
    BookKnowledgeService service;
    Product book;
    AtomicReference<BookProfile> savedProfile=new AtomicReference<>();

    @BeforeEach void setup() {
        service=new BookKnowledgeService(products,profiles,topics,links,relations,embeddings,ai,
                new ObjectMapper(),new ChatIntentService());
        book=new Product(); book.setProductId(42L); book.setName("Học lập trình từ đầu");
        book.setAuthor("Tác giả thử"); book.setDescription("Giới thiệu căn bản"); book.setStatus(false);
        when(products.findById(42L)).thenReturn(Optional.of(book));
        when(profiles.findById(42L)).thenAnswer(call -> Optional.ofNullable(savedProfile.get()));
        when(profiles.save(any())).thenAnswer(call -> { BookProfile p=call.getArgument(0);savedProfile.set(p);return p; });
        BookTopic topic=new BookTopic(); topic.setId(7L);topic.setName("Lập trình");
        when(topics.findByNameIgnoreCase("Lập trình")).thenReturn(Optional.of(topic));
    }

    @Test void publishesApprovedKnowledgeImmediatelyWithoutWaitingForEmbedding() {
        when(embeddings.available()).thenReturn(true);
        when(embeddings.document(anyString(),anyString())).thenReturn(List.of(0.2,0.8));
        BookKnowledgeService.ProfileInput input=new BookKnowledgeService.ProfileInput();
        input.setSummary("Hướng dẫn lập trình cho người mới.");
        input.setTopics(List.of("Lập trình")); input.setAudience("Người mới bắt đầu");

        var published=service.publish(42L,input);

        assertThat(published.isPublished()).isTrue();
        assertThat(published.isSemanticReady()).isFalse();
        assertThat(savedProfile.get().getSearchText()).contains("Người mới bắt đầu","Lập trình");
        var publicationOrder=inOrder(profiles,products);
        publicationOrder.verify(profiles).save(any());
        publicationOrder.verify(products).save(any());
        verify(links).save(argThat(link -> link.getProductId().equals(42L) && link.getTopicId().equals(7L)));
        verify(products).save(argThat(Product::getStatus));
        verify(embeddings,never()).document(anyString(),anyString());
    }

    @Test void indexFailureLeavesApprovedBookSearchableAndPendingForRetry() {
        when(embeddings.available()).thenReturn(true);
        when(embeddings.document(anyString(),anyString())).thenThrow(new IllegalStateException("quota"));
        BookKnowledgeService.ProfileInput input=new BookKnowledgeService.ProfileInput();
        input.setSummary("Có nội dung đã duyệt.");input.setTopics(List.of("Lập trình"));

        assertThat(service.publish(42L,input).isPublished()).isTrue();
        assertThat(service.indexProfile(42L)).isFalse();
        assertThat(service.searchableText(book)).contains("Có nội dung đã duyệt");
        assertThat(savedProfile.get().getEmbeddingJson()).isNull();
        verify(profiles,never()).storeEmbeddingIfCurrent(anyLong(),anyString(),anyString());
    }

    @Test void indexCannotOverwriteAProfileEditedDuringTheProviderRequest() {
        BookProfile profile=new BookProfile();profile.setProductId(42L);profile.setApproved(true);
        profile.setSearchText("Nội dung cũ");savedProfile.set(profile);book.setStatus(true);
        when(embeddings.available()).thenReturn(true);
        when(embeddings.document(anyString(),eq("Nội dung cũ"))).thenAnswer(call->{
            profile.setSearchText("Nội dung mới");return List.of(1.0,0.0);
        });
        when(profiles.storeEmbeddingIfCurrent(eq(42L),eq("Nội dung cũ"),anyString())).thenReturn(0);
        assertThat(service.indexProfile(42L)).isFalse();
        assertThat(profile.getSearchText()).isEqualTo("Nội dung mới");
        verify(profiles,never()).save(any());
    }

    @Test void semanticParaphraseCanRecallApprovedContentWithoutKeywordOverlap() {
        BookProfile matching=new BookProfile();matching.setProductId(42L);matching.setApproved(true);
        matching.setSummary("Rèn thói quen tập trung và quản lý thời gian.");matching.setEmbeddingJson("[1.0,0.0]");
        BookProfile unrelated=new BookProfile();unrelated.setProductId(43L);unrelated.setApproved(true);
        unrelated.setSummary("Học lập trình.");unrelated.setEmbeddingJson("[0.0,1.0]");
        BookProfile draft=new BookProfile();draft.setProductId(44L);draft.setApproved(false);
        draft.setSummary("Bản nháp chưa duyệt.");draft.setEmbeddingJson("[1.0,0.0]");
        Product other=new Product();other.setProductId(43L);Product unapproved=new Product();unapproved.setProductId(44L);
        when(profiles.findAllById(any())).thenReturn(List.of(matching,unrelated,draft));
        when(embeddings.available()).thenReturn(true);when(embeddings.query(anyString())).thenReturn(List.of(1.0,0.0));
        assertThat(service.semanticEvidence("Mình hay xao nhãng khi làm việc",List.of(book,other,unapproved)))
            .containsOnlyKeys(42L).extractingByKey(42L).asString().contains("Hồ sơ đã duyệt","quản lý thời gian");
    }

    @Test void malformedOrWrongDimensionVectorsAreNotEvidence() {
        BookProfile profile=new BookProfile();profile.setProductId(42L);profile.setApproved(true);
        profile.setSummary("Hồ sơ đã duyệt");profile.setEmbeddingJson("[1.0]");
        when(profiles.findAllById(any())).thenReturn(List.of(profile));
        when(embeddings.available()).thenReturn(true);when(embeddings.query(anyString())).thenReturn(List.of(1.0,0.0));
        assertThat(service.semanticEvidence("Mục tiêu đọc",List.of(book))).isEmpty();
        profile.setEmbeddingJson("[\"bad\",1]");
        assertThat(service.semanticEvidence("Mục tiêu đọc",List.of(book))).isEmpty();
    }

    @Test void noSemanticMatchIsDifferentFromAnUnavailableProvider() {
        BookProfile profile=new BookProfile();profile.setProductId(42L);profile.setApproved(true);
        profile.setSummary("Lập trình");profile.setEmbeddingJson("[0,1]");
        when(profiles.findAllById(any())).thenReturn(List.of(profile));when(embeddings.available()).thenReturn(true);
        when(embeddings.query(anyString())).thenReturn(List.of(1.0,0.0));
        assertThat(service.semanticRecall("Nuôi ong",List.of(book)).evaluated()).isTrue();
        assertThat(service.semanticRecall("Nuôi ong",List.of(book)).evidence()).isEmpty();
        when(embeddings.query(anyString())).thenThrow(new IllegalStateException("temporary"));
        assertThat(service.semanticRecall("Nuôi ong",List.of(book)).evaluated()).isFalse();
    }
    @Test void batchIndexKeepsEachVectorWithItsOwnCurrentApprovedText() {
        BookProfile first=new BookProfile();first.setProductId(42L);first.setApproved(true);first.setSearchText("Văn bản A");
        BookProfile second=new BookProfile();second.setProductId(43L);second.setApproved(true);second.setSearchText("Văn bản B");
        Product other=new Product();other.setProductId(43L);other.setName("Sách B");other.setStatus(true);book.setStatus(true);
        when(products.findById(43L)).thenReturn(Optional.of(other));
        when(profiles.findByApprovedTrueAndEmbeddingJsonIsNull(any())).thenReturn(List.of(first,second));
        when(embeddings.available()).thenReturn(true);
        when(embeddings.documents(anyList())).thenReturn(List.of(List.of(1.0,0.0),List.of(0.0,1.0)));
        when(profiles.storeEmbeddingIfCurrent(anyLong(),anyString(),anyString())).thenReturn(1);
        assertThat(service.reindexExisting()).containsEntry("indexed",2);
        verify(profiles).storeEmbeddingIfCurrent(42L,"Văn bản A","[1.0,0.0]");
        verify(profiles).storeEmbeddingIfCurrent(43L,"Văn bản B","[0.0,1.0]");
        verify(embeddings,never()).document(anyString(),anyString());
    }

    @Test void bibliographyIsVisibleOnlyAfterProfileApproval() {
        BookProfile profile=new BookProfile();
        profile.setProductId(42L);
        profile.setPublisher("NXB Trẻ");
        profile.setPublicationYear(2025);
        profile.setPageCount(240);
        savedProfile.set(profile);

        assertThat(service.approvedBibliography(book)).contains("Chưa có thông tin");
        profile.setApproved(true);
        assertThat(service.approvedBibliography(book))
                .contains("NXB Trẻ", "2025", "240");
    }
    @Test void relatedEvidenceUsesApprovedSpecificTopicsAndSkipsTheBroadCategory() {
        book.setStatus(true);book.setCategory(new Category(1L,"Tiểu thuyết"));
        BookProfile source=new BookProfile();source.setProductId(42L);source.setApproved(true);savedProfile.set(source);
        BookProfile target=new BookProfile();target.setProductId(43L);target.setApproved(true);
        when(profiles.findById(43L)).thenReturn(Optional.of(target));
        BookTopic broad=new BookTopic();broad.setId(1L);broad.setName("Tiểu thuyết");
        BookTopic specific=new BookTopic();specific.setId(2L);specific.setName("Bí ẩn");
        when(topics.findById(1L)).thenReturn(Optional.of(broad));when(topics.findById(2L)).thenReturn(Optional.of(specific));
        BookTopicLink broadLink=new BookTopicLink();broadLink.setProductId(42L);broadLink.setTopicId(1L);
        BookTopicLink sourceLink=new BookTopicLink();sourceLink.setProductId(42L);sourceLink.setTopicId(2L);
        BookTopicLink targetLink=new BookTopicLink();targetLink.setProductId(43L);targetLink.setTopicId(2L);
        BookTopicLink draftLink=new BookTopicLink();draftLink.setProductId(44L);draftLink.setTopicId(2L);
        when(links.findByProductId(42L)).thenReturn(List.of(broadLink,sourceLink));
        when(links.findByTopicIdIn(List.of(2L))).thenReturn(List.of(sourceLink,targetLink,draftLink));
        assertThat(service.relatedEvidence(List.of(42L))).containsOnlyKeys(43L);
        assertThat(service.relatedEvidence(List.of(42L)).get(43L)).contains("Bí ẩn");
        verify(links,never()).findByTopicIdIn(List.of(1L));
        source.setApproved(false);
        assertThat(service.relatedEvidence(List.of(42L))).isEmpty();
    }
}
