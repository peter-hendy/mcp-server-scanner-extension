package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.swing.JPanel;
import java.awt.Dimension;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsiveColumnsTest {

    @ParameterizedTest
    @CsvSource({"0,1", "250,1", "529,1", "530,2", "1200,2"})
    void fitsAsManyColumnsAsTheWidthAllowsUpToTheMaximum(int width, int expectedColumns) {
        ResponsiveColumns columns = new ResponsiveColumns(260, 2, 10);

        columns.setBounds(0, 0, width, 100);

        assertThat(columns.columns()).isEqualTo(expectedColumns);
    }

    @Test
    void eachRowIsOnlyAsTallAsItsTallestCell() {
        ResponsiveColumns columns = new ResponsiveColumns(100, 2, 10);
        JPanel shortCell = cell(40);
        JPanel tallCell = cell(90);
        JPanel lastCell = cell(30);
        columns.add(shortCell);
        columns.add(tallCell);
        columns.add(lastCell);
        columns.setBounds(0, 0, 400, 500);

        columns.doLayout();

        assertThat(shortCell.getHeight()).isEqualTo(90);
        assertThat(lastCell.getY()).isEqualTo(100);
        assertThat(lastCell.getHeight()).isEqualTo(30);
        assertThat(columns.getPreferredSize().height).isEqualTo(130);
    }

    private static JPanel cell(int height) {
        JPanel cell = new JPanel();
        cell.setPreferredSize(new Dimension(50, height));
        return cell;
    }
}
